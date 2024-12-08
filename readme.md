## Warpdrive: Secure and Seamless Remote Access  

**Warpdrive** is a lightweight, secure bridge that enables seamless access to systems hosted in private networks. It operates in two modes—**Entry** and **Exit**—to facilitate secure HTTP proxy-based communication.

---

## Modes of Operation  

Warpdrive operates in two modes:  
1. **Entry Mode** (Public HTTP Proxy)  
2. **Exit Mode** (Private Connector)

The mode determines how the application functions and which parameters are required.

### 1. **Entry Mode**  
In this mode, Warpdrive acts as an **HTTP Proxy server**. It listens for client connections and securely relays requests to connected **Exit Points**.  

**Command:**
```bash
java -jar warpdrive-1.0.0.jar entry [proxy_port] [exit_server_port] [is_forward]
```

- **Parameters**:
   - `proxy_port` (Optional): The port number for the HTTP Proxy server. Default is **8080**.  
   - `exit_server_port` (Optional): The port number for Exit Point connections. Default is **1080**.  
   - `is_forward` (Optional): Whether to forward traffic to Exit Points (`true` or `false`). Default is **true**.  

**Example**:  
```bash
java -jar warpdrive-1.0.0.jar entry 8080 1080 true
```

---

### 2. **Exit Mode**  
In this mode, Warpdrive connects to an **Entry Point** and forwards HTTP requests to **target servers** within the private network.  

**Command:**
```bash
java -jar warpdrive-1.0.0.jar exit [entry_host] [entry_port] [connections]
```

- **Parameters**:
   - `entry_host` (Optional): The address of the Entry Point. Default is `localhost`.  
   - `entry_port` (Optional): The port number of the Entry Point. Default is **8080**.  
   - `connections` (Optional): Number of concurrent connections to the Entry Point. Default is **10**.  

**Example**:  
```bash
java -jar warpdrive-1.0.0.jar exit entry.example.com 8080 10
```

---

## Key Features  

1. **No Exposed Ports**  
   - Exit Points establish **outgoing WebSocket connections** to Entry Points, bypassing firewalls and network restrictions.

2. **HTTP Proxy Support**  
   - The Entry Point operates as an **HTTP Proxy** server, allowing seamless integration with tools like browsers, Postman, or curl.

3. **Lightweight and Fast**  
   - Transfers only the necessary HTTP data, eliminating bandwidth-heavy solutions like RDP or VPNs.  

4. **Simple Deployment**  
   - A single Java application operates in either **entry** or **exit** mode with minimal configuration.

---

## Examples  

### 1. Run as Entry Point  
Run Warpdrive in **Entry Mode** to start the HTTP Proxy server:  
```bash
java -jar warpdrive-1.0.0.jar entry 8080 1080 true
```
- HTTP Proxy Port: `8080`  
- Exit Point Port: `1080`  
- Forwarding Enabled: `true`  

---

### 2. Run as Exit Point  
Run Warpdrive in **Exit Mode** to connect to the Entry Point and forward requests:  
```bash
java -jar warpdrive-1.0.0.jar exit entry.example.com 8080 10
```
- Entry Host: `entry.example.com`  
- Entry Port: `8080`  
- Concurrent Connections: `10`  

---

## Tool Configuration  

Configure developer tools to use the Entry Point as an **HTTP Proxy**:  

- **Proxy Host**: `<entrypoint-address>`  
- **Proxy Port**: `8080`  

**Example with curl:**
```bash
curl -x http://<entrypoint-address>:8080 http://your-internal-api
```

**Example in Postman**:  
- Proxy Type: **HTTP**  
- Proxy Address: `<entrypoint-address>`  
- Port: `8080`  

---

## Summary  

Warpdrive simplifies secure access to private systems by combining **HTTP Proxy functionality** with **WebSocket-based communication**.  
- **Entry Mode**: Acts as an HTTP Proxy server.  
- **Exit Mode**: Connects to Entry Points to forward requests.  

Warpdrive delivers **fast**, **lightweight**, and **secure** remote access, enabling developers to work efficiently without compromising security.