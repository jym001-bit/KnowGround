import { request } from '../request';

export type ConflictStatus = 'PENDING' | 'KEEP_LEFT' | 'KEEP_RIGHT' | 'DISMISSED';

export interface DocumentConflict {
  id: number;
  leftFileMd5: string;
  leftFileName: string;
  leftChunkId: number;
  leftValue: string;
  rightFileMd5: string;
  rightFileName: string;
  rightChunkId: number;
  rightValue: string;
  conflictKey: string;
  similarity: number;
  status: ConflictStatus;
  createdAt: string | null;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface ConflictContext {
  leftText: string;
  rightText: string;
}

export interface ConflictDetectionSettings {
  detectionEnabled: boolean;
  llmConfirmationEnabled: boolean;
}

export function fetchConflictDetectionSettings() {
  return request<ConflictDetectionSettings>({ url: '/admin/document-conflicts/settings' });
}

export function updateConflictDetectionSettings(llmConfirmationEnabled: boolean) {
  return request<ConflictDetectionSettings>({
    url: '/admin/document-conflicts/settings',
    method: 'put',
    data: { llmConfirmationEnabled }
  });
}

export function fetchDocumentConflicts(status: 'PENDING' | 'REVIEWED') {
  return request<DocumentConflict[]>({ url: '/admin/document-conflicts', params: { status } });
}

export function fetchDocumentConflictContext(id: number) {
  return request<ConflictContext>({ url: `/admin/document-conflicts/${id}/context` });
}

export function decideDocumentConflict(id: number, data: { keepFileMd5?: string; dismiss?: boolean }) {
  return request<DocumentConflict>({ url: `/admin/document-conflicts/${id}/decision`, method: 'post', data });
}

export function undoDocumentConflict(id: number) {
  return request<DocumentConflict>({ url: `/admin/document-conflicts/${id}/undo`, method: 'post' });
}
