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

## Building with Bazel

This project uses Bazel for building.

1.  **Generate `BUILD.bazel` files**

    First, you need to generate the `BUILD.bazel` files using Gazelle. Run the following command:

    ```bash
    bazel run //:gazelle
    ```

2.  **Build the application**

    *   **For your local platform:**

        ```bash
        bazel build //:owlet_monitor_go
        ```

    *   **For ARM64:**

        ```bash
        bazel build //:owlet_monitor_go --platforms=@io_bazel_rules_go//go/toolchain:linux_arm64
        ```

## Running

Once built, you can run the application from the `bazel-bin` directory.

```bash
export OWLET_USER="your-email@example.com"
export OWLET_PASS="your-password"
export OWLET_REGION="europe" # or "world"
./bazel-bin/owlet_monitor_go
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

