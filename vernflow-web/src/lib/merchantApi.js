import { userRequest } from '@/lib/userApi'

function jsonRequest(method, body) {
  return {
    method,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  }
}

export function listMerchantResources() {
  return userRequest('/api/merchant/resources')
}

export function createMerchantResource(resource) {
  return userRequest('/api/merchant/resources', jsonRequest('POST', resource))
}

export function auditMerchantResource(resource) {
  return userRequest('/api/merchant/resources/audit', jsonRequest('POST', resource))
}

export function updateMerchantResource(resourceId, resource) {
  return userRequest(
    `/api/merchant/resources/${resourceId}`,
    jsonRequest('PUT', resource),
  )
}

export function updateMerchantResourceStatus(resourceId, status) {
  return userRequest(
    `/api/merchant/resources/${resourceId}/status`,
    jsonRequest('PATCH', { status }),
  )
}

export function getMerchantDashboard() {
  return userRequest('/api/merchant/dashboard')
}

export function listMerchantProducts() {
  return userRequest('/api/merchant/products')
}

export function createMerchantProduct(product) {
  return userRequest('/api/merchant/products', jsonRequest('POST', product))
}

export function updateMerchantProduct(productId, product) {
  return userRequest(
    `/api/merchant/products/${productId}`,
    jsonRequest('PUT', product),
  )
}

export function updateMerchantProductStatus(productId, status) {
  return userRequest(
    `/api/merchant/products/${productId}/status`,
    jsonRequest('PATCH', { status }),
  )
}

export function generateMerchantContent(request) {
  return userRequest('/api/merchant/content/generate', jsonRequest('POST', request))
}
