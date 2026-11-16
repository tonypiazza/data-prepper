# Secrets Common

Cloud-agnostic building blocks for Data Prepper secrets providers such as `gcp_secrets`.

A provider supplies:

* A `SecretStoreAccessor` implementation, the only cloud-specific code. It reads the latest version,
  adds a version, optionally destroys a version, and maps failures to `SecretStoreException.Reason`.
* A configuration class extending `SecretStoreConfiguration`.
* An extension plugin that builds a `CachingSecretsSupplier`, a `SecretsPluginConfigValueTranslator` with
  its own prefix, a `SecretsPluginConfigPublisher`, and a `SecretsRefreshScheduler`, and registers the
  translator and publisher with `SecretsPluginConfigValueTranslatorExtensionProvider` and
  `SecretsPluginConfigPublisherExtensionProvider`.

## Behavior

* Payloads that parse as a JSON object are cached as maps, so `${{<prefix>:<store>:<key>}}` works.
  Any other payload is cached as a UTF-8 string.
* Write-back (`PluginConfigVariable.setValue`) runs atomically per store. When the configuration's
  `isDestroyReplacedVersion()` returns true and the accessor supports it, the version that was
  replaced is destroyed. A failed destroy is counted in `secretsVersionDestroyFailure` and never fails the write.
* Refresh metrics use the same names as the `aws-plugin` secrets refresh job.

## Known limitation

Data Prepper keeps only the first extension provider registered for an interface
(`DataPrepperExtensionPoints`). Only one secrets provider can therefore be configured per Data Prepper instance.
