'use strict';

/**
 * Lazy-loaded CPU embedding generator.
 *
 * EMBED_PROVIDER=bge (default): @xenova/transformers, mean pool — GHA/cloud.
 * EMBED_PROVIDER=qwen: @huggingface/transformers, last_token — VPS local ONNX
 *   (matches stack/backfill-qwen; documents: no Instruct prefix).
 */

const log = require('./log');
const cfg = require('./config');

let extractorPromise = null;

async function getExtractor() {
    if (!extractorPromise) {
        extractorPromise = (async () => {
            const provider = cfg.EMBED_PROVIDER;
            log.info(`[MODEL] Loading embedding model (${provider}): ${cfg.EMBED_MODEL}`);

            if (provider === 'qwen') {
                const { env, pipeline } = await import('@huggingface/transformers');
                env.cacheDir = cfg.EMBED_CACHE_DIR;
                const extractor = await pipeline('feature-extraction', cfg.EMBED_MODEL, {
                    dtype: cfg.EMBED_DTYPE,
                });
                log.info('[MODEL] Qwen model ready (last_token pooling)');
                return { provider: 'qwen', extractor };
            }

            const { env, pipeline } = await import('@xenova/transformers');
            env.cacheDir = cfg.EMBED_CACHE_DIR;
            const extractor = await pipeline('feature-extraction', cfg.EMBED_MODEL);
            log.info('[MODEL] BGE model ready (mean pooling)');
            return { provider: 'bge', extractor };
        })();
    }
    return extractorPromise;
}

/** Embed a single document text -> number[] (normalized). */
async function embed(text) {
    const { provider, extractor } = await getExtractor();
    if (provider === 'qwen') {
        const output = await extractor(text, { pooling: 'last_token', normalize: true });
        const data = Array.from(output.data);
        if (data.length !== cfg.VECTOR_DIM) {
            throw new Error(`Qwen embed dim ${data.length}, expected ${cfg.VECTOR_DIM}`);
        }
        return data;
    }
    const output = await extractor(text, { pooling: 'mean', normalize: true });
    return Array.from(output.data);
}

module.exports = { embed };
