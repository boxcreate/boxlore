'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const SCRIPT = path.resolve(__dirname, '../remediate-qdrant.js');

// Runs remediate-qdrant.js in a child process with Turso, Qdrant and the cap trim replaced, so
// the whole dry run executes without a network. The preload patches the shared modules before
// the script destructures them.
function runDryRun() {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'remediate-'));
    const preload = path.join(dir, 'mocks.js');
    const lib = (name) => JSON.stringify(path.join(__dirname, name));
    fs.writeFileSync(preload, `
        const turso = require(${lib('turso')});
        const qdrant = require(${lib('qdrant')});
        const chartCountries = require(${lib('chart-countries')});
        const trim = require(${lib('trim-episode-caps')});
        // 600 chart shows (above the safety minimum); show 3 is flagged but has no points.
        const chartRows = Array.from({ length: 600 }, (_, i) => [i + 1, String(1000 + i + 1)]);
        Object.assign(turso, {
            assertEnv() {}, beginStep() {}, healthCheck: async () => {}, flushStats() {},
            getStats: () => ({ reads: 0, writes: 0 }),
            execute: async () => { throw new Error('a dry run must not write'); },
            fetchAllPaged: async ({ buildPage }) =>
                buildPage(null, 10).sql.includes('qdrant_vectorized = 1') ? [[2, '1002'], [3, '1003']] : chartRows,
        });
        Object.assign(qdrant, {
            assertEnv() {},
            collectionInfo: async () => ({ pointsCount: 3, status: 'green' }),
            ensurePayloadIndex: async () => {},
            scrollAll: async () => [1, 2, 3].map((n) => ({ id: 'p' + n, payload: { podcast_id: 2, published_date: n } })),
        });
        chartCountries.loadCountriesByItunesId = async () => new Map(chartRows.map((r) => [r[1], ['us']]));
        trim.trimEpisodeCaps = async () => ({ trimmedShows: 1, trimmedShowIds: ['2'], excessPoints: 1, deletedPoints: 0 });
    `);
    const env = { ...process.env };
    delete env.GITHUB_STEP_SUMMARY;
    const out = spawnSync(process.execPath, ['--require', preload, SCRIPT, '--dry-run', '--skip-quantization'], {
        env,
        encoding: 'utf8',
    });
    fs.rmSync(dir, { recursive: true, force: true });
    return out;
}

describe('remediate-qdrant', () => {
    it('finishes a dry run and queues holes plus trimmed shows for healing', () => {
        const out = runDryRun();
        assert.equal(out.status, 0, out.stdout + out.stderr);
        assert.match(out.stdout, /Flags to reset \(holes \+ trimmed shows\): 2/);
        assert.match(out.stdout, /Step 8: Enable quantization[\s\S]*Done\. Turso cost/);
    });
});
