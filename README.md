# JMeter Auto-Correlation Plugin

One-click detection and correlation of dynamic values in Apache JMeter test scripts.

Automatically finds session IDs, CSRF tokens, OAuth tokens, hidden form fields, and other dynamic values in your recorded scripts, then adds the appropriate extractors and replaces hardcoded values — similar to LoadRunner's auto-correlation feature.

## Supported Dynamic Value Types

| Type | Examples |
|------|----------|
| Session IDs | JSESSIONID, PHPSESSID, ASP.NET Session IDs (cookie and URL-rewrite formats) |
| CSRF Tokens | csrf_token, __RequestVerificationToken, authenticity_token |
| OAuth / Bearer Tokens | access_token, refresh_token, JWT tokens |
| Hidden Form Fields | _sourcePage, __fp, __VIEWSTATE, __EVENTVALIDATION |
| Correlation IDs | x-correlation-id, x-request-id, trace-id |
| Nonces & Timestamps | nonce, _wpnonce, timestamp |
| API Keys | api_key, x-api-key |
| SAML Tokens | SAMLResponse, SAMLRequest, RelayState |

## Installation

### Option A: Manual Install

1. Download the latest JAR from the [Releases](https://github.com/vasanthshanmugam/jmeter-auto-correlation-plugin/releases) page
2. Copy the JAR to your JMeter `lib/ext/` directory
3. Restart JMeter

### Option B: Build from Source

```bash
git clone https://github.com/vasanthshanmugam/jmeter-auto-correlation-plugin.git
cd jmeter-auto-correlation-plugin
mvn clean package
```

Copy `target/jmeter-auto-correlation-plugin-1.0.0-jar-with-dependencies.jar` to JMeter's `lib/ext/` directory.

## How to Use

1. **Open your test plan** in JMeter (recorded or manually created)

2. **Add the plugin**: Right-click on Thread Group > Add > Post Processors > **Auto-Correlation Detector**

3. **Click "Click to Correlate"**: The plugin will:
   - Replay your test plan (1 thread, 1 iteration) to capture live responses
   - Scan all responses for dynamic values
   - Cross-reference detected values with subsequent requests
   - Show only values that are actually reused (need correlation)

4. **Review the results table**:
   - Each row shows a detected dynamic value, its type, source sampler, and how many subsequent requests use it
   - High-confidence candidates are pre-selected
   - Uncheck any you don't want to correlate

5. **Click "Apply Selected"**: The plugin automatically:
   - Adds a Regular Expression Extractor under the source sampler
   - Replaces hardcoded values in subsequent request parameters, URL paths, and HTTP headers with `${variable_name}`

6. **Run your test** to verify the correlations work correctly

## Compatibility

- Apache JMeter 5.5+
- Java 8+
- Works with HTTP samplers, DummySampler, and other AbstractSampler implementations

## Building from Source

```bash
# Build
mvn clean package

# Run tests
mvn test

# Build without tests
mvn clean package -DskipTests
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

[Apache License 2.0](LICENSE)

## Author

Vasanth - [https://github.com/vasanthshanmugam](https://github.com/vasanthshanmugam)
