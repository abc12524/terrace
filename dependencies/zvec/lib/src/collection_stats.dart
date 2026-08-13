// Copyright 2025-present the zvec project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'dart:ffi';

import 'package:ffi/ffi.dart';

import 'zvec_bindings.dart';
import 'zvec_library.dart';

ZvecBindings get _b => ZvecLibrary.bindings;

/// Statistics for a collection.
class CollectionStats {
  CollectionStats._(this._ptr);

  /// Create from a native pointer. Used internally by Collection.
  factory CollectionStats.fromNativePtr(Pointer<zvec_collection_stats_t> ptr) =>
      CollectionStats._(ptr);

  final Pointer<zvec_collection_stats_t> _ptr;

  /// Total number of documents in the collection.
  int get docCount => _b.zvec_collection_stats_get_doc_count(_ptr);

  /// Number of indexes in the collection.
  int get indexCount => _b.zvec_collection_stats_get_index_count(_ptr);

  /// Get the index name at the given position.
  String getIndexName(int index) {
    final ptr = _b.zvec_collection_stats_get_index_name(_ptr, index);
    return ptr.cast<Utf8>().toDartString();
  }

  /// Get the index completeness (0.0 to 1.0) at the given position.
  double getIndexCompleteness(int index) =>
      _b.zvec_collection_stats_get_index_completeness(_ptr, index);

  /// Get all index names and their completeness as a map.
  Map<String, double> get indexes {
    final result = <String, double>{};
    final count = indexCount;
    for (var i = 0; i < count; i++) {
      result[getIndexName(i)] = getIndexCompleteness(i);
    }
    return result;
  }

  /// Destroy the native stats object.
  void destroy() {
    _b.zvec_collection_stats_destroy(_ptr);
  }
}
