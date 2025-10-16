# Owlet Monitor Go

This is a Go application to monitor Owlet devices. It fetches real-time vitals from the Owlet API and exposes them as Prometheus metrics.

It's heavily based on [owlet_monitor Python API](https://sourcegraph.com/github.com/mbevand/owlet_monitor/-/blob/owlet_monitor).
All code was translated to golang with Gemini so I can run the code in a Raspberry PI with little resources.

I run a Prometheus server that scrapes the metrics from this apps, persists to disk and uses `remote_rewrite` to push into my grafana cloud
free tier account, this allows me to see the real values and not a dot aggregated every 10 minutes, which is helpful as my kid suffers
from oxygen saturation drops.

## Features

- Fetches real-time vitals from Owlet devices, including:
  - Heart rate
  - Oxygen level
  - Base station battery level
  - Charging status
  - Base station on/off status
  - Sensor battery level
  - Baby movement
- Exposes the collected vitals as Prometheus metrics on port 9090.
- Supports multiple Owlet regions (defaults to "europe").
- Supports the EU version of the Owlet Socket from 2025.

## Configuration

The application is configured via the following environment variables:

- `OWLET_USER`: Your Owlet account email address.
- `OWLET_PASS`: Your Owlet account password.
- `OWLET_REGION`: The Owlet region to use. Can be `europe` or `world`. Defaults to `europe`.

## Building/Running with Bazel

This project uses Bazel for building and gazelle to keep the build files up to date.

### Running locally

```bash
  export OWLET_USER="your-email@example.com"
  export OWLET_PASS="your-password"
  export OWLET_REGION="europe" # or "world"
  bazel run //owlet-monitor
```

### Building for Raspberry PI (arm64)

```bash
  bazel build //:owlet-monitor-arm64
  scp $(realpath bazel-bin/owlet-monitor/owlet-monitor-arm64_/owlet-monitor-arm64) <ip>:
  ssh pi@<ip>
  export OWLET_USER="your-email@example.com"
  export OWLET_PASS="your-password"
  export OWLET_REGION="europe" # or "world"
  ./owlet-monitor-arm64
```

The application will start fetching data and expose metrics on `http://localhost:9090/metrics`.

## Prometheus Integration

In order to scrape my metrics I have a prometheus config similar to this:

```yaml
global:
  scrape_interval: 5s
remote_write:
  - url: <prometheus-push-url>
    basic_auth:
      username: <username>
      password: <password>
scrape_configs:
  - job_name: node
    static_configs:
      - targets:
        - "localhost:9090"
```

I get `<prometheus-push-url>` from grafana cloud together with the `<username>` and `<password>`.

Then in my raspberry pi I installed prometheus with:

```bash
sudo apt-get install prometheus
```

Put my `prometheus.yaml` into `/etc/prometheus/prometheus.yml` and modified `/etc/defaults/prometheus` to say:

```bash
# Set the command-line arguments to pass to the server.
# Due to shell escaping, to pass backslashes for regexes, you need to double
# them (\\d for \d). If running under systemd, you need to double them again
# (\\\\d to mean \d), and escape newlines too.
ARGS="--storage.tsdb.path=/var/lib/prometheus/db --web.listen-address=0.0.0.0:9091"
```

## Running with systemd

To run the Owlet monitor as a service on a systemd-based OS like Raspberry Pi OS, you can create a systemd service file.

### Copy the required files

```bash

scp owlet-monitor.service pi@<ip>
bazel build //:owlet-monitor-arm64
scp $(realpath bazel-bin/owlet-monitor/owlet-monitor-arm64_/owlet-monitor-arm64) pi@<ip>:
ssh pi@<ip>
sudo mv owlet-monitor.service /etc/systemd/system/
sudo mkdir -p /usr/local/bin
sudo cp owlet-monitor-arm64 /usr/local/bin/owlet-monitor
sudo mkdir -p /etc/owlet-monitor
```

### Create the Environment File

The service loads your Owlet credentials from a separate environment file.

**Create the configuration file:**

Create a file at `/etc/owlet-monitor/owlet-monitor.conf` with your credentials:

```bash
OWLET_USER="your-email@example.com"
OWLET_PASS="your-password"
OWLET_REGION="europe"
```

**Set permissions:**

Secure this file so that only the root user can read it:

```bash
sudo chown root:root /etc/owlet-monitor/owlet-monitor.conf
sudo chmod 600 /etc/owlet-monitor/owlet-monitor.conf
```

### Manage the Service

Use `systemctl` to manage your new service.

- **Reload systemd:**

  ```bash
  sudo systemctl daemon-reload
  ```

- **Enable the service to start on boot:**

  ```bash
  sudo systemctl enable owlet-monitor.service
  ```

- **Start the service:**

  ```bash
  sudo systemctl start owlet-monitor.service
  ```

- **Check the status of the service:**

  ```bash
  sudo systemctl status owlet-monitor.service
  ```
