This document outlines the technical design and mathematical formulation of the BoxCast personalized recommendation engine. The system leverages semantic embeddings, user engagement signals, and a vector search architecture to generate highly relevant episode suggestions.

**Vector Embedding Generation and Input Formatting**
For each episode in the listening history, the engine constructs a structured text block for semantic indexing and vector matching. To prevent noise from social media links, sponsors, and tracking redirects, raw descriptions undergo a cleaning process. First, HTML tags are stripped. The text is then truncated at the first occurrence of common boilerplate keywords (e.g., links:, follow us, socials, sponsors, ad choices, patreon, instagram, twitter, facebook, discord, website, support the show, merch, subscribe). Remaining HTTP/HTTPS/WWW URLs are stripped, and the text is capped at 1000 characters.

The cleaned description is consolidated with metadata to create a unified text string:
"Episode: {Episode Title}. Description: {Cleaned Description}. Podcast: {Podcast Title}. Genres: {Categories}. Host: {Author}"

This string is passed to the @cf/baai/bge-large-en-v1.5 model on Cloudflare Workers AI to generate a 1024-dimensional dense vector representation.

**User Taste Vector Weighting Formulation**
To synthesize a query vector representing a user's listening profile, history items are grouped into clusters by genre (up to 3 clusters). Within each cluster, the individual episode vectors are merged into a single weighted average vector. The weight assigned to each episode vector depends on the user's play duration, completion state, and explicit like signals.

Let R be the completion ratio defined as:
R = min(progressMs / durationMs, 1.0)

The raw engagement weight (w_raw) is:
w_raw = 0.3 + 0.7 * R

If the episode is completed, the weight is adjusted:
w_completed = max(w_raw, 0.9)  [if isCompleted is true; else w_completed = w_raw]

If the episode is liked by the user, an explicit positive signal boost is added:
w_liked = w_completed + 0.3  [if isLiked is true; else w_liked = w_completed]

The final weight (w_final) is capped at 1.5:
w_final = min(w_liked, 1.5)

For a cluster containing N episodes, the user's cluster taste vector (V_cluster) is computed as the element-wise weighted average:
V_cluster = (sum_{i=1}^N (w_final_i * v_i)) / (sum_{i=1}^N w_final_i)
where v_i represents the 1024-dimensional embedding vector of episode i.

**Qdrant Vector Retrieval and Recency Re-ranking**
The system queries the Qdrant vector database using the calculated V_cluster. Qdrant performs a cosine similarity search against the indexed episode corpus. To ensure recommendations are not dominated by old content while still preserving relevance, a mild age-decay discount is applied to the raw similarity scores.

Let t_age represent the age of the episode in days since publication:
t_age = max(0, (t_now - t_published) / 86400)

The recency boost multiplier is defined as:
b_recency = 1.0 / (1.0 + 0.003 * t_age)

The final adjusted score (S_adjusted) used for ranking is:
S_adjusted = S_similarity * b_recency
where S_similarity is the raw cosine similarity score. This decay curve applies a soft penalty of approximately 30% to episodes that are 100 days old.

**Exclusion Filtering and Diversity Enforcement**
To present a varied selection, the engine interleaves the sorted candidate lists from all clusters. It then filters candidates against multiple criteria:
1. Language compatibility based on client geolocation.
2. NSFW keyword filter lists checking titles, descriptions, and categories.
3. Explicit exclusion of podcasts the user is already subscribed to.
4. Exclusion of exact episode titles already present in the user's history.
5. In Pass 1 (Strict Discovery Mode), episodes from podcast shows already in the user's history are fully excluded to encourage new show discovery. 
6. Diversification is enforced by capping recommendations at 1 episode per podcast show.

If Pass 1 returns fewer than 15 items, the system triggers Pass 2 (Fallback Discovery Mode). Pass 2 relaxes the show exclusion constraint, allowing episodes from previously listened shows to be recommended (excluding the exact episodes already played). The final list is capped at the top 60 recommended episodes.
