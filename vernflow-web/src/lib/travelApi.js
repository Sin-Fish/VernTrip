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

export function createTravelPlan(request) {
  return userRequest("/api/tourism/trips/plan", jsonRequest("POST", {
    ...request,
    days: Number(request.days),
    budget: Number(request.budget),
  }));
}

export function adjustTravelPlan(planId, adjustment) {
  return userRequest(
    `/api/tourism/plans/${planId}/adjust`,
    jsonRequest("POST", adjustment),
  );
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
