import { userRequest } from "@/lib/userApi";

function jsonRequest(method, body) {
  return {
    method,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  };
}

export function createTravelPlan(request, options = {}) {
  return userRequest("/api/tourism/trips/plan", { ...jsonRequest("POST", {
    ...request,
    days: Number(request.days),
    budget: Number(request.budget),
  }), signal: options.signal });
}

export function listTourismResources(city) {
  const params = new URLSearchParams({ city });
  return userRequest(`/api/tourism/resources?${params.toString()}`);
}

export function recommendTourismResources(request) {
  return userRequest("/api/tourism/recommendations", jsonRequest("POST", {
    ...request,
    budget: Number(request.budget),
  }));
}

export function adjustTravelPlan(planId, adjustment) {
  return userRequest(
    `/api/tourism/plans/${planId}/adjust`,
    jsonRequest("POST", adjustment),
  );
}

export function getTravelPlan(planId) {
  return userRequest(`/api/tourism/plans/${planId}`);
}

export function saveTravelPlan(planId) {
  return userRequest(`/api/tourism/plans/${planId}/save`, {
    method: "POST",
  });
}

export function removeSavedTravelPlan(planId) {
  return userRequest(`/api/tourism/plans/${planId}/save`, {
    method: "DELETE",
  });
}

export function listSavedTravelPlans() {
  return userRequest("/api/tourism/plans");
}

export function getFortuneProfile() {
  return userRequest("/api/tourism/fortune/profile");
}

export function saveFortuneProfile(preferences) {
  return userRequest("/api/tourism/fortune/profile", jsonRequest("PUT", preferences));
}

export function drawTravelFortune(request, options = {}) {
  return userRequest("/api/tourism/fortune", { ...jsonRequest("POST", request), signal: options.signal });
}

export function generateResourceVideo(resourceId, request) {
  return userRequest(
    "/api/me/videos",
    jsonRequest("POST", { ...request, resourceId, durationSeconds: Number(request.durationSeconds) }),
  );
}

export function getVideoTask(taskId) {
  return userRequest(`/api/me/video-tasks/${taskId}`);
}

export function videoFileUrl(taskId) {
  return `/api/tourism/videos/${taskId}/file`;
}
