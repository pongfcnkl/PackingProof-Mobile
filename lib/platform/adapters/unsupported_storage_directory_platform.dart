import '../contracts/storage_directory_platform.dart';
import '../platform_capabilities.dart';
import '../platform_exceptions.dart';

class UnsupportedStorageDirectoryPlatform implements StorageDirectoryPlatform {
  const UnsupportedStorageDirectoryPlatform();

  Never _unsupported() => throw const CapabilityUnavailableException(
    PlatformCapability.customRecordingDirectory,
  );

  @override
  Future<String?> pickDirectory() async => _unsupported();

  @override
  Future<bool> requestAccess() async => _unsupported();

  @override
  Future<String> validateDirectory(String path) async => _unsupported();
}
