'use strict';

/**
 * Keyset pagination for Turso/sqld HTTP responses.
 * Avoids RESPONSE_TOO_LARGE by never materializing unbounded SELECT payloads.
 */

/**
 * @param {object} deps
 * @param {(sql: string, args?: any[]) => Promise<any>} deps.execute
 * @param {(res: any) => any[][]} deps.rows
 * @param {object} opts
 * @param {(afterId: number|string|null, limit: number) => { sql: string, args?: any[] }} opts.buildPage
 * @param {(row: any[]) => number|string} opts.rowId
 * @param {number} [opts.pageSize=300]
 * @param {number} [opts.maxRows=Infinity] stop after this many rows (still pages safely)
 * @returns {Promise<any[][]>}
 */
async function fetchAllPaged(deps, opts) {
    const { execute, rows } = deps;
    const {
        buildPage,
        rowId,
        pageSize = 300,
        maxRows = Infinity,
    } = opts;

    if (typeof buildPage !== 'function' || typeof rowId !== 'function') {
        throw new Error('fetchAllPaged requires buildPage and rowId');
    }
    if (!Number.isFinite(pageSize) || pageSize < 1) {
        throw new Error(`invalid pageSize: ${pageSize}`);
    }

    const all = [];
    let after = null;

    for (;;) {
        const remaining = maxRows === Infinity ? pageSize : maxRows - all.length;
        if (remaining <= 0) break;
        const limit = Math.min(pageSize, remaining);
        const { sql, args = [] } = buildPage(after, limit);
        const page = rows(await execute(sql, args));
        if (page.length === 0) break;
        // Defensive: never exceed maxRows even if the server ignores LIMIT.
        const take = page.slice(0, limit);
        all.push(...take);
        after = rowId(take[take.length - 1]);
        if (take.length < limit) break;
    }

    return all;
}

module.exports = { fetchAllPaged };
