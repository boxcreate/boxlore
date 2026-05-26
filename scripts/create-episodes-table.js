#!/usr/bin/env node

/**
 * Initialize the separate 'episodes' table and indices in Turso
 */

const TURSO_URL = process.env.TURSO_URL?.replace('libsql://', 'https://');
const TURSO_TOKEN = process.env.TURSO_AUTH_TOKEN;

if (!TURSO_URL || !TURSO_TOKEN) {
    console.error("Missing TURSO_URL or TURSO_AUTH_TOKEN");
    process.exit(1);
}

async function executeSQL(sql, args = []) {
    const response = await fetch(`${TURSO_URL}/v2/pipeline`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${TURSO_TOKEN}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            requests: [{
                type: "execute",
                stmt: { 
                    sql, 
                    args: args.map(a => ({ 
                        type: a === null ? "null" : "text", 
                        value: a === null ? null : String(a) 
                    })) 
                }
            }, { type: "close" }]
        })
    });
    if (!response.ok) {
        throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }
    const res = await response.json();
    if (res.results && res.results[0] && res.results[0].type === "error") {
        throw new Error(`SQL execution error: ${res.results[0].error.message}`);
    }
    return res;
}

async function main() {
    console.log("[SCHEMA] Initializing database schema updates...");

    // 1. Create episodes table
    const createTableSQL = `
        CREATE TABLE IF NOT EXISTS episodes (
            id INTEGER PRIMARY KEY,
            podcast_id INTEGER,
            title TEXT,
            description TEXT,
            published_date INTEGER,
            duration INTEGER,
            enclosure_url TEXT,
            image_url TEXT,
            explicit INTEGER,
            chapters_url TEXT,
            transcript_url TEXT,
            persons TEXT,
            transcripts TEXT,
            vector F32(384),
            created_at INTEGER,
            FOREIGN KEY (podcast_id) REFERENCES podcasts(id) ON DELETE CASCADE
        )
    `;
    console.log("[SCHEMA] Creating table 'episodes' if not exists...");
    await executeSQL(createTableSQL);

    // 2. Create indices
    console.log("[SCHEMA] Creating index 'idx_episodes_podcast_id'...");
    await executeSQL("CREATE INDEX IF NOT EXISTS idx_episodes_podcast_id ON episodes(podcast_id)");

    console.log("[SCHEMA] Creating index 'idx_episodes_published_date'...");
    await executeSQL("CREATE INDEX IF NOT EXISTS idx_episodes_published_date ON episodes(published_date DESC)");

    console.log("[SCHEMA] Creating index 'idx_episodes_vector' on vector column...");
    try {
        await executeSQL("CREATE INDEX IF NOT EXISTS idx_episodes_vector ON episodes(vector)");
        console.log("[SCHEMA] Index 'idx_episodes_vector' successfully verified/created.");
    } catch (e) {
        console.warn("[SCHEMA] Note: Indexing vector column failed:", e.message);
    }

    console.log("[SCHEMA] Schema initialization complete!");
}

main().catch(err => {
    console.error("[SCHEMA] Schema migration failed:", err);
    process.exit(1);
});
