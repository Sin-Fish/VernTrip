/**
 * 把图片（URL 或本地路径）降采样后转为 JPEG base64（不含 data: 前缀），
 * 用于图生视频（可灵 image-to-video 的 first_frame）。
 * 降采样保证请求体不超后端 10MB POST 上限。
 *
 * @param source  图片地址（同源静态资源）
 * @param options { maxWidth=1280, quality=0.85 }
 * @returns {Promise<string>} 纯 base64 字符串
 */
export function imageToBase64(source, { maxWidth = 1280, quality = 0.85 } = {}) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => {
      try {
        const scale = Math.min(1, maxWidth / image.naturalWidth)
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
        const context = canvas.getContext('2d')
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        const dataUrl = canvas.toDataURL('image/jpeg', quality)
        resolve(dataUrl.slice(dataUrl.indexOf(',') + 1))
      } catch (error) {
        reject(error)
      }
    }
    image.onerror = () => reject(new Error('图片加载失败，无法生成视频首帧。'))
    image.src = source
  })
}
