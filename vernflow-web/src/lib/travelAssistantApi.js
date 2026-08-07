import { userRequest } from "@/lib/userApi";

export function analyzeTravelConversation(prompt, current, history = []) {
  return userRequest("/api/tourism/conversations/analyze", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      prompt,
      current: {
        ...current,
        days: current.days ? Number(current.days) : null,
        budget: current.budget ? Number(current.budget) : null,
        interests: [...(current.interests || [])],
      },
      history: history.slice(-8),
    }),
  });
}
