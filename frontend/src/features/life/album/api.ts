import { http } from '@/core/http/client'

export interface PhotoVO {
  id: number
  albumId: number
  title: string | null
  url: string
  fileId: number | null
  takenAt: string | null
}

export interface AlbumVO {
  id: number
  name: string
  description: string | null
  coverUrl: string | null
  photoCount: number
  createdAt: string
  photos: PhotoVO[] | null
}

export interface AlbumPayload {
  name: string
  description?: string | null
}

export interface PhotoPayload {
  title?: string | null
  url?: string | null
  fileId?: number | null
  takenAt?: string | null
}

export const albumApi = {
  list() {
    return http.get<AlbumVO[]>('/albums')
  },
  detail(id: number) {
    return http.get<AlbumVO>(`/albums/${id}`)
  },
  create(payload: AlbumPayload) {
    return http.post<AlbumVO>('/albums', payload)
  },
  update(id: number, payload: AlbumPayload) {
    return http.put<AlbumVO>(`/albums/${id}`, payload)
  },
  remove(id: number) {
    return http.delete<void>(`/albums/${id}`)
  },
  addPhoto(albumId: number, payload: PhotoPayload) {
    return http.post<PhotoVO>(`/albums/${albumId}/photos`, payload)
  },
  removePhoto(photoId: number) {
    return http.delete<void>(`/albums/photos/${photoId}`)
  },
}
