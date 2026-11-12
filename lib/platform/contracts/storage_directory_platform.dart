abstract interface class StorageDirectoryPlatform {
  Future<bool> requestAccess();

  Future<String?> pickDirectory();

  Future<String> validateDirectory(String path);
}
