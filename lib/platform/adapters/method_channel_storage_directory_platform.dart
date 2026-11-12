import 'package:flutter/services.dart';

import '../contracts/storage_directory_platform.dart';

class MethodChannelStorageDirectoryPlatform
    implements StorageDirectoryPlatform {
  const MethodChannelStorageDirectoryPlatform();

  static const MethodChannel _channel = MethodChannel(
    'app.packingproof.mobile/storage_directory',
  );

  @override
  Future<bool> requestAccess() async =>
      await _channel.invokeMethod<bool>('requestAccess') ?? false;

  @override
  Future<String?> pickDirectory() =>
      _channel.invokeMethod<String>('pickDirectory');

  @override
  Future<String> validateDirectory(String path) async {
    final String? result = await _channel.invokeMethod<String>(
      'validateDirectory',
      <String, Object?>{'path': path},
    );
    if (result == null || result.isEmpty) {
      throw PlatformException(code: 'storage_directory_unavailable');
    }
    return result;
  }
}
