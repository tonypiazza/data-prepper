# GCP Plugin

Provides `GcpCredentialsSupplier` (from `gcp-plugin-api`) to Data Prepper plugins that use Google Cloud,
the same way `aws-plugin` provides `AwsCredentialsSupplier`.

## Using it from a plugin

Add a `GcpConfig` field (the shared `gcp:` block) to the plugin's configuration, and inject `GcpCredentialsSupplier`
through the `@DataPrepperPluginConstructor`:

```java
final GoogleCredentials credentials = gcpCredentialsSupplier.getCredentials(config.getGcpConfig().toCredentialsOptions());
```

Call `getCredentials` in your plugin constructor so malformed configuration fails at startup. Building credentials does
not fetch a token, so authentication failures (missing permissions, an unreachable token endpoint) surface on first use.

## Configuration

Extension defaults, in `data-prepper-config.yaml`:

```yaml
extensions:
  gcp:
    configurations:
      default:
        project_id: my-project
        auth_mode: adc
```

A plugin's `gcp:` block:

```yaml
gcp:
  project_id: my-project
  auth_mode: service_account_key       # or adc
  client_email: sa@my-project.iam.gserviceaccount.com
  private_key: ${{aws_secrets:sa-key:private_key}}
  token_uri: https://oauth2.googleapis.com/token   # optional
  scopes: https://www.googleapis.com/auth/cloud-platform   # optional, list or single value
```

* A plugin gets the credentials from its own `gcp:` block if that sets `auth_mode`. Otherwise it gets
  `extensions.gcp.configurations.default` if that sets `auth_mode`. Otherwise it gets Application Default Credentials (ADC).
* `adc` covers `GOOGLE_APPLICATION_CREDENTIALS` (including Workload Identity Federation and
  `impersonated_service_account` files), gcloud user credentials, and the metadata server (GKE Workload Identity, GCE, Cloud Run).
* `token_uri` must be `https://oauth2.googleapis.com` on the default port. This guard comes from the ml-commons
  `google_cloud` connector, and stops the signed JWT from being sent anywhere else.
* Values under `extensions.gcp.configurations` cannot use `${{...}}` references. A plugin's own `gcp:` block can.
  Using `service_account_key` in the extension default therefore means a plaintext private key in
  `data-prepper-config.yaml`. Prefer `adc` there, with `GOOGLE_APPLICATION_CREDENTIALS` or Workload Identity.
* A plugin's `scopes` and `project_id` are kept when its credentials come from the extension default or ADC.
* `getDefaultProjectId()` returns the extension default's `project_id`. If none is set, it returns the ADC project, except
  when the extension default uses `service_account_key`, where you should set `project_id` explicitly.
  The ADC project is looked up once and cached until restart, including when ADC is unavailable.
* A `service_account_key` extension default is built at startup, so a bad key or `token_uri` fails fast.
* `token_uri` is only valid with `service_account_key`, and must not contain user info.
* Named configurations are not supported yet. Only `default` is accepted.

## GCP Secret Manager (`gcp_secrets`)

Reference GCP Secret Manager secrets from pipeline configuration:

* `${{gcp_secrets:<store>}}` returns the whole payload of the secret's latest version.
  Use this for secrets that hold a single value.
* `${{gcp_secrets:<store>:<key>}}` parses the payload as a JSON object and returns `<key>`.
  Using this form on a payload that isn't a JSON object fails at startup.

Stores are configured under `extensions.gcp.secrets` in `data-prepper-config.yaml` or in a pipeline's `extension` block:

```yaml
extensions:
  gcp:
    secrets:
      jira-oauth:
        secret_id: jira-oauth
        gcp:
          auth_mode: adc
        refresh_interval: PT1H
        disable_refresh: false
        skip_validation_on_start: false
        destroy_replaced_version: true
      shared-token:
        secret_id: projects/other-project/secrets/api-token
```

* `secret_id` (required): a secret ID, or a full name `projects/<project>/secrets/<secret>`. A short ID is resolved
  against the store's `gcp.project_id`, then the `extensions.gcp.configurations.default` project,
  then the Application Default Credentials project.
* `gcp` (optional): credentials for this store. If it's omitted, `extensions.gcp.configurations.default` is used,
  then Application Default Credentials. These values can't themselves use `${{...}}` references, because
  extension configuration isn't variable-expanded.
* `destroy_replaced_version` (default `true`): when a plugin writes a secret back (for example the Jira and
  Confluence sources rotating OAuth tokens), the version that was replaced is destroyed. GCP bills every
  enabled or disabled version, so without this, each write adds a billed version.

Permissions: reading needs `secretmanager.versions.access`. Write-back needs `secretmanager.versions.add`.
With `destroy_replaced_version: true` it also needs `secretmanager.versions.destroy`
(`roles/secretmanager.secretVersionManager`).

The plugin calls the Secret Manager v1 REST API directly, so it adds no Google Cloud SDK dependency.
It works in both the legacy and OSGi plugin modes.

**Limitation:** only one cloud secrets provider can be configured per Data Prepper instance. If
`aws_secrets` and `gcp_secrets` are both configured, only one is used. References to the other are passed
through as literal strings. See #5816.
