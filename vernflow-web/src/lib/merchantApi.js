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
