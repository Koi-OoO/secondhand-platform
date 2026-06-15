import http from './index'

/** 单张上传 */
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/file/upload', formData)
}

/** 批量上传（最多 9 张） */
export function uploadImagesBatch(files) {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  return http.post('/file/upload-batch', formData)
}
