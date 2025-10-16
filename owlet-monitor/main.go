package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// Global state and configuration
var (
	authToken       string
	expireTime      time.Time
	dsns            []string
	httpClient      *http.Client
	region          string
	regionConfs     map[string]RegionConfig
	vitalsGaugeVecs = make(map[string]*prometheus.GaugeVec)
)

// RegionConfig holds the configuration for a specific Owlet region
type RegionConfig struct {
	URLMini   string `json:"url_mini"`
	URLSignin string `json:"url_signin"`
	URLBase   string `json:"url_base"`
	APIKey    string `json:"apiKey"`
	AppID     string `json:"app_id"`
	AppSecret string `json:"app_secret"`
}

// Vitals holds the real-time monitoring data from the Owlet device.
// Fields are pointers to distinguish between a zero value and a field not being present.
type Vitals struct {
	Alert         *int     `json:"alrt,omitempty"`
	Aps           *int     `json:"aps,omitempty"`
	BaseBattery   *int     `json:"bat,omitempty"`
	Bp            *int     `json:"bp,omitempty"`
	Bsb           *int     `json:"bsb,omitempty"`
	Bso           *int     `json:"bso,omitempty"`
	SensorBattery *int     `json:"btt,omitempty"`
	Charging      *int     `json:"chg,omitempty"`
	HeartRate     *int     `json:"hr,omitempty"`
	Hardware      *string  `json:"hw,omitempty"`
	Mrs           *int     `json:"mrs,omitempty"`
	Mst           *float64 `json:"mst,omitempty"`
	BabyMovement  *int     `json:"mv,omitempty"`
	Mvb           *int     `json:"mvb,omitempty"`
	Onm           *int     `json:"onm,omitempty"`
	Ota           *int     `json:"ota,omitempty"`
	Oxygen        *int     `json:"ox,omitempty"`
	Oxta          *int     `json:"oxta,omitempty"`
	Rsi           *int     `json:"rsi,omitempty"`
	Sb            *int     `json:"sb,omitempty"`
	Sc            *int     `json:"sc,omitempty"`
	Srf           *int     `json:"srf,omitempty"`
	Ss            *int     `json:"ss,omitempty"`
	St            *int     `json:"st,omitempty"`
}

// Structs for API communication
type GoogleAuthRequest struct {
	Email             string `json:"email"`
	Password          string `json:"password"`
	ReturnSecureToken bool   `json:"returnSecureToken"`
}

type GoogleAuthResponse struct {
	IDToken string `json:"idToken"`
}

type MiniTokenResponse struct {
	MiniToken string `json:"mini_token"`
}

type AylaAuthRequest struct {
	AppID     string `json:"app_id"`
	AppSecret string `json:"app_secret"`
	Provider  string `json:"provider"`
	Token     string `json:"token"`
}

type AylaAuthResponse struct {
	AccessToken string `json:"access_token"`
	ExpiresIn   int    `json:"expires_in"`
}

type Device struct {
	DSN string `json:"dsn"`
}

type DeviceContainer struct {
	Device Device `json:"device"`
}

type Property struct {
	Name          string      `json:"name"`
	Value         interface{} `json:"value"`
	DataUpdatedAt string      `json:"data_updated_at"`
}

type PropertyContainer struct {
	Property Property `json:"property"`
}

func newVitalsGauge(name, help string) *prometheus.GaugeVec {
	gauge := prometheus.NewGaugeVec(
		prometheus.GaugeOpts{
			Name: fmt.Sprintf("owlet_%s", name),
			Help: help,
		},
		[]string{"dsn"},
	)
	prometheus.MustRegister(gauge)
	return gauge
}

func init() {
	// Initialize HTTP client
	httpClient = &http.Client{Timeout: 30 * time.Second}

	// Initialize Prometheus metrics
	vitalsGaugeVecs["heart_rate"] = newVitalsGauge("heart_rate", "Current heart rate.")
	vitalsGaugeVecs["oxygen_level"] = newVitalsGauge("oxygen_level", "Current oxygen level.")
	vitalsGaugeVecs["base_battery_level"] = newVitalsGauge("base_battery_level", "Current base station battery level.")
	vitalsGaugeVecs["charging_status"] = newVitalsGauge("charging_status", "Current charging status (1 for charging, 0 for not charging).")
	vitalsGaugeVecs["base_station_on"] = newVitalsGauge("base_station_on", "Base station on status (1 for on, 0 for off).")
	vitalsGaugeVecs["sensor_battery"] = newVitalsGauge("sensor_battery", "Amount of minutes the sensor battery hsa")
	vitalsGaugeVecs["baby_movement"] = newVitalsGauge("baby_movement", "How much is the baby moving")

	// Initialize region configurations
	regionConfs = map[string]RegionConfig{
		"world": {
			URLMini:   "https://ayla-sso.owletdata.com/mini/",
			URLSignin: "https://user-field-1a2039d9.aylanetworks.com/api/v1/token_sign_in",
			URLBase:   "https://ads-field-1a2039d9.aylanetworks.com/apiv1",
			APIKey:    "AIzaSyCsDZ8kWxQuLJAMVnmEhEkayH1TSxKXfGA",
			AppID:     "sso-prod-3g-id",
			AppSecret: "sso-prod-UEjtnPCtFfjdwIwxqnC0OipxRFU",
		},
		"europe": {
			URLMini:   "https://ayla-sso.eu.owletdata.com/mini/",
			URLSignin: "https://user-field-eu-1a2039d9.aylanetworks.com/api/v1/token_sign_in",
			URLBase:   "https://ads-field-eu-1a2039d9.aylanetworks.com/apiv1",
			APIKey:    "AIzaSyDm6EhV70wudwN3iOSq3vTjtsdGjdFLuuM",
			AppID:     "OwletCare-Android-EU-fw-id",
			AppSecret: "OwletCare-Android-EU-JKupMPBoj_Npce_9a95Pc8Qo0Mw",
		},
	}
}

func fatal(err error) {
	log.SetOutput(os.Stderr)
	log.Fatal(err)
}

func login() error {
	if authToken != "" && time.Now().Before(expireTime) {
		return nil
	}

	log.Println("Logging in")

	// Get credentials from environment
	owletUser := os.Getenv("OWLET_USER")
	owletPass := os.Getenv("OWLET_PASS")
	region = os.Getenv("OWLET_REGION")
	if region == "" {
		region = "europe" // default region
	}

	if owletUser == "" || owletPass == "" {
		return fmt.Errorf("OWLET_USER or OWLET_PASS env var is not defined")
	}
	conf, ok := regionConfs[region]
	if !ok {
		return fmt.Errorf("OWLET_REGION env var '%s' not recognised", region)
	}

	// 1. Authenticate against Firebase
	gAuthReqBody, _ := json.Marshal(GoogleAuthRequest{
		Email:             owletUser,
		Password:          owletPass,
		ReturnSecureToken: true,
	})
	req, _ := http.NewRequest("POST", fmt.Sprintf("https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=%s", conf.APIKey), bytes.NewBuffer(gAuthReqBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Android-Package", "com.owletcare.owletcare")
	req.Header.Set("X-Android-Cert", "2A3BC26DB0B8B0792DBE28E6FFDC2598F9B12B74")

	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("google auth failed with status: %s", resp.Status)
	}
	var gAuthResp GoogleAuthResponse
	if err := json.NewDecoder(resp.Body).Decode(&gAuthResp); err != nil {
		return err
	}
	jwt := gAuthResp.IDToken

	// 2. Get mini_token
	req, _ = http.NewRequest("GET", conf.URLMini, nil)
	req.Header.Set("Authorization", jwt)
	req.Header.Set("Accept", "application/json")
	resp, err = httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("mini token fetch failed with status: %s", resp.Status)
	}
	var miniTokenResp MiniTokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&miniTokenResp); err != nil {
		return err
	}
	miniToken := miniTokenResp.MiniToken

	// 3. Get Ayla access_token
	aylaAuthReqBody, _ := json.Marshal(AylaAuthRequest{
		AppID:     conf.AppID,
		AppSecret: conf.AppSecret,
		Provider:  "owl_id",
		Token:     miniToken,
	})
	req, _ = http.NewRequest("POST", conf.URLSignin, bytes.NewBuffer(aylaAuthReqBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	resp, err = httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("ayla auth failed with status: %s, body: %s", resp.Status, string(bodyBytes))
	}
	var aylaAuthResp AylaAuthResponse
	if err := json.NewDecoder(resp.Body).Decode(&aylaAuthResp); err != nil {
		return err
	}

	authToken = aylaAuthResp.AccessToken
	// Re-auth 60 seconds before expiry
	expireTime = time.Now().Add(time.Duration(aylaAuthResp.ExpiresIn-60) * time.Second)
	log.Printf("Auth token obtained, valid until %s", expireTime.Format(time.RFC1123))
	return nil
}

func fetchDSN() error {
	if len(dsns) > 0 {
		return nil
	}
	log.Println("Getting DSN")
	conf := regionConfs[region]
	req, _ := http.NewRequest("GET", conf.URLBase+"/devices.json", nil)
	req.Header.Set("Authorization", "auth_token "+authToken)
	req.Header.Set("Accept", "application/json")

	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("fetch dsn failed with status: %s, body: %s", resp.Status, string(bodyBytes))
	}

	var devices []DeviceContainer
	if err := json.NewDecoder(resp.Body).Decode(&devices); err != nil {
		return err
	}

	if len(devices) == 0 {
		return fmt.Errorf("found zero Owlet monitors")
	}

	for _, dev := range devices {
		dsns = append(dsns, dev.Device.DSN)
		log.Printf("Found Owlet monitor device serial number %s", dev.Device.DSN)
	}
	return nil
}

func reactivate(dsn string) error {
	conf := regionConfs[region]
	url := fmt.Sprintf("%s/dsns/%s/properties/APP_ACTIVE/datapoints.json", conf.URLBase, dsn)
	payload := `{"datapoint":{"metadata":{},"value":1}}`
	req, _ := http.NewRequest("POST", url, bytes.NewBufferString(payload))
	req.Header.Set("Authorization", "auth_token "+authToken)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("reactivate failed for DSN %s with status: %s, body: %s", dsn, resp.Status, string(bodyBytes))
	}
	return nil
}

func fetchProps() (map[string]map[string]Property, error) {
	allProps := make(map[string]map[string]Property)
	conf := regionConfs[region]

	for _, dsn := range dsns {
		if err := reactivate(dsn); err != nil {
			log.Printf("Warning: could not reactivate DSN %s: %v", dsn, err)
			continue // Continue to next device
		}

		url := fmt.Sprintf("%s/dsns/%s/properties.json", conf.URLBase, dsn)
		req, _ := http.NewRequest("GET", url, nil)
		req.Header.Set("Authorization", "auth_token "+authToken)
		req.Header.Set("Accept", "application/json")

		resp, err := httpClient.Do(req)
		if err != nil {
			log.Printf("Warning: could not fetch props for DSN %s: %v", dsn, err)
			continue
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			bodyBytes, _ := io.ReadAll(resp.Body)
			log.Printf("Warning: fetch props for DSN %s returned status: %s, body: %s", dsn, resp.Status, string(bodyBytes))
			continue
		}

		var props []PropertyContainer
		if err := json.NewDecoder(resp.Body).Decode(&props); err != nil {
			log.Printf("Warning: could not decode props for DSN %s: %v", dsn, err)
			continue
		}

		deviceProps := make(map[string]Property)
		for _, p := range props {
			deviceProps[p.Property.Name] = p.Property
		}
		allProps[dsn] = deviceProps
	}
	return allProps, nil
}

// getFormattedTime is a helper function to parse and format the timestamp.
func getFormattedTime(updatedAt string) string {
	if updatedAt == "" {
		return "Unknown Time"
	}
	t, err := time.Parse(time.RFC3339Nano, updatedAt)
	if err != nil {
		return updatedAt // fallback to original string on error
	}
	return t.Local().Format("2006-01-02 15:04:05")
}

func recordVitals(dsn string, props map[string]Property) {
	rtv, ok := props["REAL_TIME_VITALS"]
	if !ok || rtv.Value == nil {
		log.Println("no vitals")
		return
	}

	valueStr, ok := rtv.Value.(string)
	if !ok {
		log.Println("vitals value is not a string")
		return
	}

	var vitals Vitals
	// The value from the API is a JSON string, so we unmarshal it into our struct.
	if err := json.Unmarshal([]byte(valueStr), &vitals); err != nil {
		log.Printf("Warning: could not unmarshal vitals JSON: %v", err)
		// As a fallback, print the raw string if unmarshaling fails.
		fmt.Printf("%s: %s\n", getFormattedTime(rtv.DataUpdatedAt), valueStr)
		return
	}

	// Update Prometheus metrics
	if vitals.HeartRate != nil {
		vitalsGaugeVecs["heart_rate"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.HeartRate))
	}
	if vitals.Oxygen != nil {
		vitalsGaugeVecs["oxygen_level"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.Oxygen))
	}
	if vitals.BaseBattery != nil {
		vitalsGaugeVecs["base_battery_level"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.BaseBattery))
	}
	if vitals.Charging != nil {
		vitalsGaugeVecs["charging_status"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.Charging))
	}
	if vitals.Bso != nil {
		vitalsGaugeVecs["base_station_on"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.Bso))
	}
	if vitals.SensorBattery != nil {
		vitalsGaugeVecs["sensor_battery"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.SensorBattery))
	}
	if vitals.BabyMovement != nil {
		vitalsGaugeVecs["baby_movement"].With(prometheus.Labels{"dsn": dsn}).Set(float64(*vitals.BabyMovement))
	}

	prettyJSON, err := json.MarshalIndent(vitals, "", "  ")

	if err != nil {
		log.Fatalf("failed to marshal JSON: %s", err)
	}

	// Using %+v will print the struct with field names for better readability.
	fmt.Printf("%s: %s\n", getFormattedTime(rtv.DataUpdatedAt), prettyJSON)
}

func loop() {
	if err := login(); err != nil {
		fatal(err)
	}
	if err := fetchDSN(); err != nil {
		fatal(err)
	}

	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		// Initial run without waiting for ticker
		runMonitoringCycle()

		// Wait for next tick
		select {
		case <-ticker.C:
			continue
		case <-context.Background().Done():
			return
		}
	}
}

func runMonitoringCycle() {
	if err := login(); err != nil { // Check token and re-login if needed
		log.Printf("Error during periodic login check: %v", err)
		time.Sleep(5 * time.Second) // Wait before retrying
		return
	}

	allDeviceProps, err := fetchProps()
	if err != nil {
		log.Printf("Error fetching props: %v", err)
		return
	}

	for dsn, props := range allDeviceProps {
		recordVitals(dsn, props)
	}
}

func main() {
	// Expose the /metrics endpoint in a background goroutine
	http.Handle("/metrics", promhttp.Handler())
	go func() {
		log.Println("Metrics server starting on :9090")
		if err := http.ListenAndServe(":9090", nil); err != nil {
			log.Printf("Error starting metrics server: %v", err)
		}
	}()

	loop()
}
