---
name: social-post-writer
description: Write and adapt social media posts for a personal software project, especially browser features and product updates. Use when creating, rewriting, or adapting posts for Reddit, X/Twitter, or Threads. Focus on clear product problems, concrete solutions, natural developer voice, platform-specific formatting, and discussion-friendly wording without sounding promotional or clickbait-heavy.
---

# Social Post Writer

## Workflow

1. Identify the core product idea:
   - What problem does the feature solve?
   - Who experiences the problem?
   - What does the user currently have to do?
   - What does the new feature make easier?
   - What is the simplest demonstration of the feature?

2. Reduce the idea to a simple structure:
   - Problem
   - Friction with the existing workflow
   - Solution
   - Optional additional feature
   - Optional question for discussion

3. Lead with the concrete feature or problem.
   - Prefer specific hooks such as:
     - "I wanted a faster way to..."
     - "Switching between X and Y on mobile was annoying, so I built..."
     - "What if you could..."
   - Avoid vague hooks such as:
     - "This always annoyed me..."
     - "I was tired of modern browsers..."
     - "So I built my own browser."
   - Avoid framing the project as an attack on existing browsers.

4. Make the post understandable without prior context.
   - Do not assume the reader saw previous posts.
   - Explain the problem in one or two sentences.
   - Explain the solution in one or two sentences.
   - Let the attached video demonstrate the interaction instead of over-explaining it.

5. Keep the tone:
   - Casual
   - Clear
   - Curious
   - Developer-to-user rather than company-to-customer
   - Confident without being arrogant
   - Open to criticism and alternative workflows

6. Do not oversell the feature.
   - Avoid words such as "revolutionary", "game-changing", "ultimate", "next-generation", or similar hype.
   - Do not claim that a feature is objectively better for everyone.
   - Prefer "I wanted...", "I tried...", "I made...", and "I think..." when appropriate.

7. When the post is about a browser feature, focus on the user's workflow rather than the browser itself.
   - Do not make "I built a browser" the main selling point unless the user specifically wants a browser announcement.
   - Explain why the feature is useful in a real situation.
   - If the feature solves a small problem, treat it as a small but useful improvement.

8. When relevant, mention customizability.
   - If the browser supports custom search providers, mention that users can add their own providers.
   - If shortcuts are supported, mention them briefly.
   - Do not bury the primary feature under a list of secondary features.

9. Adapt the copy to each platform.

### Reddit

- Write a descriptive, conversational title.
- Avoid clickbait and vague emotional titles.
- Explain the problem and solution clearly.
- Keep enough context for someone unfamiliar with the project.
- Prefer discussion over promotion.
- End with a natural question when useful.
- Ask about the user's workflow rather than asking for generic validation.
- Avoid excessive emojis.
- Do not use marketing language.
- If the project is still unreleased, do not make the post sound like an advertisement.

Recommended structure:

Title:

> Specific problem + concrete solution

Body:

> Brief context  
> The problem  
> The solution  
> Optional additional functionality  
> Discussion question

### X / Twitter

- Make the first sentence immediately understandable.
- Keep the post concise.
- Prioritize the strongest idea.
- Remove unnecessary background information.
- Use short paragraphs or line breaks when they improve readability.
- Do not add a discussion question unless it fits naturally.
- If the post is part of a thread, make each post independently understandable.

### Threads

- Use a conversational tone.
- Slightly more personal context is acceptable.
- Explain the problem before the solution.
- Keep the writing natural rather than optimized for maximum engagement.
- End with a question when it can encourage useful discussion.

10. If the user provides one draft intended for multiple platforms:

- Do not simply copy the same text three times.
- Preserve the same core message.
- Rewrite each version for the platform's natural style.
- Keep terminology and product claims consistent across all versions.

11. If the user provides a previous post or previous feedback:

- Use it only when it materially improves the new post.
- Preserve successful patterns from previous posts.
- Avoid repeating wording unnecessarily.
- If previous feedback revealed confusion about a feature, make the new explanation more explicit.

12. If the user is posting a video:

- Assume the video demonstrates the interaction.
- Keep the text focused on the problem and why the interaction exists.
- Do not describe every visual action shown in the video.
- Suggest a short, realistic example query or scenario when it would make the video easier to understand.

13. Before finalizing, check:

- Is the problem immediately clear?
- Is the solution immediately clear?
- Does the title accurately describe the post?
- Does the post avoid unnecessary criticism of competing products?
- Does it avoid clickbait?
- Does it sound like a real developer rather than a marketing department?
- Does the video add information rather than repeat the entire post?
- Is each platform version appropriate for that platform?
- Are product claims accurate and supported by the user's information?

## Rules

- Always preserve the user's actual product capabilities. Never invent features.
- Always distinguish between existing functionality and planned functionality.
- Never imply that a feature exists if the user only plans to implement it.
- Never invent download links, release dates, GitHub repositories, user numbers, or other product facts.
- Never describe AI as a feature unless the user explicitly wants to discuss it.
- Do not introduce AI-related claims merely because AI was used during development.
- Never frame the project as "better than every existing browser."
- Prefer a concrete user problem over a generic statement about dissatisfaction with browsers.
- Avoid clickbait titles that hide what the feature actually does.
- Avoid defensive language about development methods, technology choices, or AI unless the user specifically wants to address criticism.
- Keep technical details out of the post unless they are directly relevant to the audience or feature.
- When the user asks for English copy, write natural English rather than translating Russian sentence-by-sentence.
- When the user asks for multiple platforms, provide separate versions for Reddit, X/Twitter, and Threads.
- If the user's draft is already strong, make only the changes needed to improve clarity and naturalness.
- Do not make the writing excessively polished or corporate.

## References & Benchmark Examples

When detailed platform guidance or real-world post references are needed, read:

- [Platform Guidelines](file:///Users/ilya/CODING/browser/.agents/skills/social-post-writer/references/platform-guidelines.md) — Character limits, formatting rules, and hooks for Reddit, X, and Threads.
- [Good Reddit Posts](file:///Users/ilya/CODING/browser/.agents/skills/social-post-writer/references/good-reddit-post.md) — Proven high-engagement examples focusing on concrete problems and solutions.
- [Bad Reddit Posts](file:///Users/ilya/CODING/browser/.agents/skills/social-post-writer/references/bad-reddit-post.md) — Common anti-patterns (announcement tone, feature dumps, lack of specific hook).

