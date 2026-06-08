import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http_parser/http_parser.dart';
import '../../../core/di/injection.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/network/network_error_mapper.dart';
import '../../cases/models/case_model.dart';

class ImageModel {
  final String id, caseId, uploadedById, imageUrl;
  final String? caption;
  final int sortOrder;
  const ImageModel({
    required this.id,
    required this.caseId,
    required this.uploadedById,
    required this.imageUrl,
    this.caption,
    required this.sortOrder,
  });
  factory ImageModel.fromJson(Map<String, dynamic> j) => ImageModel(
        id: j['id'] as String,
        caseId: j['caseId'] as String,
        uploadedById: j['uploadedById'] as String,
        imageUrl: j['imageUrl'] as String,
        caption: j['caption'] as String?,
        sortOrder: j['sortOrder'] as int,
      );
}

class ImagesScreen extends StatefulWidget {
  final String caseId;
  final CaseModel? caseModel;
  const ImagesScreen({super.key, required this.caseId, this.caseModel});

  @override
  State<ImagesScreen> createState() => _ImagesScreenState();
}

class _ImagesScreenState extends State<ImagesScreen>
    with WidgetsBindingObserver {
  static const Set<String> _allowedExtensions = {
    '.jpg',
    '.jpeg',
    '.png',
    '.webp'
  };
  static const int _maxImageBytes = AppConstants.maxImageSizeMb * 1024 * 1024;
  static const Duration _presignedUrlRefreshInterval = Duration(minutes: 10);
  static const Duration _imageErrorRefreshCooldown = Duration(seconds: 30);

  final FlutterSecureStorage _storage = getIt<FlutterSecureStorage>();
  List<ImageModel> _images = [];
  bool _loading = false;
  bool _uploading = false;
  final Set<String> _deletingImageIds = <String>{};
  String? _uploadStatus;
  double? _uploadProgress;
  Timer? _refreshTimer;
  DateTime? _lastImageErrorRefreshAt;
  String? _currentUserId;
  String? _currentUserRole;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadCurrentUser();
    _load();
    _refreshTimer = Timer.periodic(_presignedUrlRefreshInterval, (_) {
      if (!mounted || _uploading) return;
      _load(showLoader: false);
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _refreshTimer?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted && !_uploading) {
      _load(showLoader: false);
    }
  }

  void _refreshOnImageError() {
    if (!mounted || _uploading || _deletingImageIds.isNotEmpty) return;
    final now = DateTime.now();
    if (_lastImageErrorRefreshAt != null &&
        now.difference(_lastImageErrorRefreshAt!) <
            _imageErrorRefreshCooldown) {
      return;
    }
    _lastImageErrorRefreshAt = now;
    _load(showLoader: false);
  }

  Future<void> _loadCurrentUser() async {
    final userId = await _storage.read(key: AppConstants.userIdKey);
    final role = await _storage.read(key: AppConstants.userRoleKey);
    if (!mounted) return;
    setState(() {
      _currentUserId = userId;
      _currentUserRole = role;
    });
  }

  bool get _isPrivileged =>
      _currentUserRole == 'ADMIN' || _currentUserRole == 'CHAIRPERSON';

  bool get _isCaseCreator {
    final createdById = widget.caseModel?.createdById;
    return createdById != null && _currentUserId == createdById;
  }

  bool get _canUploadImages => _isPrivileged || _isCaseCreator;

  bool _canMutateImage(ImageModel image) =>
      _isPrivileged || _isCaseCreator || image.uploadedById == _currentUserId;

  void _openImageViewer(int initialIndex) {
    if (_images.isEmpty) return;
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _ImageViewerScreen(
          images: List<ImageModel>.from(_images),
          initialIndex: initialIndex,
          onUpdateCaption: _updateCaption,
          canEditCaption: _canMutateImage,
        ),
      ),
    );
  }

  Future<String?> _promptCaption({String? initialCaption}) async {
    return showDialog<String?>(
      context: context,
      builder: (context) => _CaptionDialog(
        title: 'Image caption',
        initialCaption: initialCaption,
        emptyActionLabel: 'Skip',
      ),
    );
  }

  Future<ImageModel?> _updateCaption(ImageModel image, String caption) async {
    if (!_canMutateImage(image)) return null;
    try {
      final dio = getIt<Dio>();
      final resp = await dio.patch(
        '/api/images/${image.id}/caption',
        data: {'caption': caption},
      );
      final updated = ImageModel.fromJson(resp.data as Map<String, dynamic>);
      if (!mounted) return updated;
      setState(() {
        final idx = _images.indexWhere((e) => e.id == image.id);
        if (idx >= 0) {
          _images[idx] = updated;
        }
      });
      return updated;
    } on DioException catch (e) {
      final message = mapNetworkError(e);
      if (!mounted) return null;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
      return null;
    }
  }

  Future<void> _deleteImage(ImageModel image) async {
    if (!_canMutateImage(image)) return;
    if (_deletingImageIds.contains(image.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete image?'),
        content: const Text('This action cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _deletingImageIds.add(image.id));
    try {
      final dio = getIt<Dio>();
      await dio.delete('/api/images/${image.id}');
      if (!mounted) return;
      setState(() => _images.removeWhere((i) => i.id == image.id));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Image deleted')),
      );
    } on DioException catch (e) {
      final message = mapNetworkError(e);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } finally {
      if (mounted) {
        setState(() => _deletingImageIds.remove(image.id));
      }
    }
  }

  Future<void> _load({bool showLoader = true}) async {
    if (showLoader && mounted) {
      setState(() => _loading = true);
    }
    try {
      final dio = getIt<Dio>();
      final resp = await dio.get('/api/cases/${widget.caseId}/images');
      if (!mounted) return;
      setState(() => _images = (resp.data as List)
          .map((e) => ImageModel.fromJson(e as Map<String, dynamic>))
          .toList());
    } finally {
      if (showLoader && mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _upload() async {
    if (!_canUploadImages) return;
    if (_uploading) return;
    if (_images.length >= AppConstants.maxImagesPerCase) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Maximum 40 images reached')));
      return;
    }
    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 75,
      maxWidth: 1600,
      maxHeight: 1600,
    );
    if (picked == null) return;
    if (!mounted) return;

    final pathLower = picked.path.toLowerCase();
    final isAllowedType = _allowedExtensions.any(pathLower.endsWith);
    if (!isAllowedType) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text('Unsupported image format. Use JPG, PNG, or WEBP.')),
      );
      return;
    }

    setState(() {
      _uploading = true;
      _uploadStatus = 'Compressing image...';
      _uploadProgress = null;
    });

    final compressed = await _compressForUpload(File(picked.path));
    if (!mounted) return;
    if (compressed == null) {
      setState(() {
        _uploading = false;
        _uploadStatus = null;
        _uploadProgress = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not prepare image for upload.')),
      );
      return;
    }

    final sizeBytes = await compressed.length();
    if (!mounted) return;
    if (sizeBytes > _maxImageBytes) {
      setState(() {
        _uploading = false;
        _uploadStatus = null;
        _uploadProgress = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Image exceeds 10 MB limit.')),
      );
      return;
    }
    final caption = await _promptCaption();
    if (!mounted) return;
    if (caption == null) {
      setState(() {
        _uploading = false;
        _uploadStatus = null;
        _uploadProgress = null;
      });
      return;
    }

    setState(() {
      _uploadStatus = 'Uploading image...';
      _uploadProgress = 0;
    });
    try {
      final dio = getIt<Dio>();
      final payload = <String, dynamic>{
        'file': await MultipartFile.fromFile(
          compressed.path,
          filename: 'collateral-${DateTime.now().millisecondsSinceEpoch}.jpg',
          contentType: MediaType('image', 'jpeg'),
        ),
      };
      if (caption.isNotEmpty) {
        payload['caption'] = caption;
      }
      final formData = FormData.fromMap(payload);
      await dio.post(
        '/api/cases/${widget.caseId}/images',
        data: formData,
        onSendProgress: (sent, total) {
          if (!mounted || total <= 0) return;
          setState(() => _uploadProgress = sent / total);
        },
      );
      if (!mounted) return;
      setState(() {
        _uploadStatus = 'Refreshing images...';
        _uploadProgress = null;
      });
      await _load();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Image uploaded')),
      );
    } on DioException catch (e) {
      final message = mapNetworkError(e);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } finally {
      if (mounted) {
        setState(() {
          _uploading = false;
          _uploadStatus = null;
          _uploadProgress = null;
        });
      }
    }
  }

  Future<File?> _compressForUpload(File source) async {
    final targetPath = '${Directory.systemTemp.path}'
        '${Platform.pathSeparator}case-image-${DateTime.now().microsecondsSinceEpoch}.jpg';

    XFile? compressed;
    try {
      compressed = await FlutterImageCompress.compressAndGetFile(
        source.path,
        targetPath,
        minWidth: 1600,
        minHeight: 1600,
        quality: 75,
        format: CompressFormat.jpeg,
      );
    } catch (_) {
      return null;
    }

    return compressed == null ? null : File(compressed.path);
  }

  @override
  Widget build(BuildContext context) {
    final content = _loading
        ? const Center(child: CircularProgressIndicator())
        : RefreshIndicator(
            onRefresh: _load,
            child: _images.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: const [
                      SizedBox(height: 180),
                      Center(child: Text('No images uploaded')),
                    ],
                  )
                : GridView.builder(
                    padding: const EdgeInsets.all(8),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 3,
                            crossAxisSpacing: 4,
                            mainAxisSpacing: 4),
                    itemCount: _images.length,
                    itemBuilder: (ctx, i) {
                      final img = _images[i];
                      final isDeleting = _deletingImageIds.contains(img.id);
                      return ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Material(
                          color: Colors.transparent,
                          child: InkWell(
                            onTap: () => _openImageViewer(i),
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                Image.network(
                                  img.imageUrl,
                                  fit: BoxFit.cover,
                                  errorBuilder: (context, error, stackTrace) {
                                    WidgetsBinding.instance
                                        .addPostFrameCallback((_) {
                                      _refreshOnImageError();
                                    });
                                    return Container(
                                      color: Theme.of(context)
                                          .colorScheme
                                          .surfaceContainerHighest,
                                      alignment: Alignment.center,
                                      child: const Icon(
                                          Icons.broken_image_outlined),
                                    );
                                  },
                                ),
                                if (isDeleting)
                                  Container(
                                    color: Colors.black.withValues(alpha: 0.28),
                                    alignment: Alignment.center,
                                    child: const SizedBox(
                                      height: 24,
                                      width: 24,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ),
                                if (_canMutateImage(img))
                                  Positioned(
                                    top: 4,
                                    right: 4,
                                    child: Material(
                                      color:
                                          Colors.black.withValues(alpha: 0.55),
                                      borderRadius: BorderRadius.circular(14),
                                      child: InkWell(
                                        borderRadius: BorderRadius.circular(14),
                                        onTap: isDeleting
                                            ? null
                                            : () => _deleteImage(img),
                                        child: Padding(
                                          padding: const EdgeInsets.all(4),
                                          child: isDeleting
                                              ? const SizedBox(
                                                  height: 16,
                                                  width: 16,
                                                  child:
                                                      CircularProgressIndicator(
                                                    strokeWidth: 2,
                                                    color: Colors.white,
                                                  ),
                                                )
                                              : const Icon(
                                                  Icons.delete_outline,
                                                  size: 16,
                                                  color: Colors.white,
                                                ),
                                        ),
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          );

    return Scaffold(
      body: Stack(
        children: [
          Positioned.fill(child: content),
          if (_uploading)
            Positioned(
              left: 12,
              right: 12,
              bottom: 12,
              child: _UploadProgressPanel(
                status: _uploadStatus ?? 'Uploading image...',
                progress: _uploadProgress,
              ),
            ),
        ],
      ),
      floatingActionButton: _canUploadImages
          ? FloatingActionButton(
              onPressed: _uploading ? null : _upload,
              child: Badge(
                label:
                    Text('${_images.length}/${AppConstants.maxImagesPerCase}'),
                child: _uploading
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.add_photo_alternate),
              ),
            )
          : null,
    );
  }
}

class _UploadProgressPanel extends StatelessWidget {
  final String status;
  final double? progress;

  const _UploadProgressPanel({
    required this.status,
    required this.progress,
  });

  @override
  Widget build(BuildContext context) {
    final value = progress;
    return Material(
      elevation: 8,
      borderRadius: BorderRadius.circular(8),
      color: Theme.of(context).colorScheme.surface,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            SizedBox(
              height: 26,
              width: 26,
              child: CircularProgressIndicator(
                strokeWidth: 3,
                value: value,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                value == null
                    ? status
                    : '$status ${(value * 100).clamp(0, 100).round()}%',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ImageViewerScreen extends StatefulWidget {
  final List<ImageModel> images;
  final int initialIndex;
  final Future<ImageModel?> Function(ImageModel image, String caption)
      onUpdateCaption;
  final bool Function(ImageModel image) canEditCaption;

  const _ImageViewerScreen({
    required this.images,
    required this.initialIndex,
    required this.onUpdateCaption,
    required this.canEditCaption,
  });

  @override
  State<_ImageViewerScreen> createState() => _ImageViewerScreenState();
}

class _ImageViewerScreenState extends State<_ImageViewerScreen> {
  late final PageController _pageController;
  late int _currentIndex;
  late final List<ImageModel> _images;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
    _pageController = PageController(initialPage: widget.initialIndex);
    _images = List<ImageModel>.from(widget.images);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _editCaption() async {
    final current = _images[_currentIndex];
    final caption = await showDialog<String?>(
      context: context,
      builder: (context) => _CaptionDialog(
        title: 'Edit caption',
        initialCaption: current.caption,
        emptyActionLabel: 'Clear',
      ),
    );
    if (caption == null || !mounted) return;

    final updated = await widget.onUpdateCaption(current, caption);
    if (!mounted || updated == null) return;
    setState(() => _images[_currentIndex] = updated);
  }

  @override
  Widget build(BuildContext context) {
    final current = _images[_currentIndex];
    final caption = (current.caption ?? '').trim();
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text('${_currentIndex + 1}/${widget.images.length}'),
        actions: [
          if (widget.canEditCaption(current))
            IconButton(
              tooltip: 'Edit caption',
              onPressed: _editCaption,
              icon: const Icon(Icons.edit_outlined),
            ),
        ],
      ),
      body: Stack(
        children: [
          PageView.builder(
            controller: _pageController,
            itemCount: _images.length,
            onPageChanged: (index) => setState(() => _currentIndex = index),
            itemBuilder: (context, index) {
              final image = _images[index];
              return Center(
                child: InteractiveViewer(
                  minScale: 0.8,
                  maxScale: 4.0,
                  child: Image.network(
                    image.imageUrl,
                    fit: BoxFit.contain,
                    errorBuilder: (context, error, stackTrace) => const Icon(
                      Icons.broken_image_outlined,
                      color: Colors.white70,
                      size: 48,
                    ),
                  ),
                ),
              );
            },
          ),
          Positioned(
            left: 12,
            right: 12,
            bottom: 12,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.65),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                caption.isEmpty ? 'No caption' : caption,
                style: const TextStyle(color: Colors.white),
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CaptionDialog extends StatefulWidget {
  final String title;
  final String? initialCaption;
  final String emptyActionLabel;

  const _CaptionDialog({
    required this.title,
    required this.initialCaption,
    required this.emptyActionLabel,
  });

  @override
  State<_CaptionDialog> createState() => _CaptionDialogState();
}

class _CaptionDialogState extends State<_CaptionDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialCaption ?? '');
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _close(String? value) {
    FocusScope.of(context).unfocus();
    Navigator.of(context).pop(value);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _controller,
        maxLength: 500,
        maxLines: 3,
        textInputAction: TextInputAction.done,
        decoration: const InputDecoration(
          hintText: 'Add optional context for this collateral image',
        ),
        onSubmitted: (_) => _close(_controller.text.trim()),
      ),
      actions: [
        TextButton(
          onPressed: () => _close(null),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () => _close(''),
          child: Text(widget.emptyActionLabel),
        ),
        FilledButton(
          onPressed: () => _close(_controller.text.trim()),
          child: const Text('Save'),
        ),
      ],
    );
  }
}
