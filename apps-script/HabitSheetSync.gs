/**
 * HabitSheetSync.gs
 *
 * Deploy this as a Google Apps Script Web App (Execute as: Me, Access: Anyone
 * with the link). The Google Sheet it's bound to acts as the full backing
 * store for the Android app - it holds both the habit definitions (name,
 * icon, schedule, reminder, the 4 Laws text) and the day-by-day completion
 * log, so that deleting and reinstalling the app doesn't lose anything: the
 * app can pull everything back from here.
 *
 * POST (from the app, pushing local changes):
 *   { "habits": [ { "syncId": "...", "name": "...", "emoji": "...", ... } ],
 *     "logs":   [ { "syncId": "...", "date": "2026-08-10", "completed": true, "completedAt": 172... } ] }
 *   Both arrays are upserted by syncId (habits) / syncId+date (logs) - safe to resend.
 *
 * GET (from the app, restoring after a reinstall):
 *   Returns { "ok": true, "habits": [...], "logs": [...] } - the full current dataset.
 *
 * Full step-by-step setup: /docs/SETUP_GOOGLE_SHEETS.md
 */

const DEFS_SHEET_NAME = 'HabitDefinitions';
const DEFS_COLUMNS = [
  'syncId', 'name', 'emoji', 'colorHex', 'activeDays', 'timeOfDay',
  'reminderEnabled', 'reminderHour', 'reminderMinute',
  'lawObvious', 'lawAttractive', 'lawEasy', 'lawSatisfying',
  'createdAtEpochDay', 'archived'
];
const DEFS_HEADER_LABELS = [
  'Sync ID', 'Name', 'Emoji', 'Color', 'Active Days (bitmask)', 'Time Of Day',
  'Reminder On', 'Reminder Hour', 'Reminder Minute',
  'Law: Obvious', 'Law: Attractive', 'Law: Easy', 'Law: Satisfying',
  'Created (epoch day)', 'Archived'
];

const LOGS_SHEET_NAME = 'HabitLogs';
const LOGS_COLUMNS = ['syncId', 'date', 'completed', 'completedAt'];
const LOGS_HEADER_LABELS = ['Sync ID', 'Date', 'Completed', 'Completed At'];

function doPost(e) {
  try {
    const body = JSON.parse((e.postData && e.postData.contents) || '{}');
    const habits = body.habits || [];
    const logs = body.logs || [];

    upsertRows_(DEFS_SHEET_NAME, DEFS_COLUMNS, DEFS_HEADER_LABELS, habits, ['syncId']);
    upsertRows_(LOGS_SHEET_NAME, LOGS_COLUMNS, LOGS_HEADER_LABELS, logs, ['syncId', 'date']);

    return jsonResponse_({ ok: true, habitsWritten: habits.length, logsWritten: logs.length });
  } catch (err) {
    return jsonResponse_({ ok: false, error: String(err) });
  }
}

/** Restore endpoint: returns everything currently stored, in the shape the app expects. */
function doGet(e) {
  try {
    const habits = readRows_(DEFS_SHEET_NAME, DEFS_COLUMNS, DEFS_HEADER_LABELS);
    const logs = readRows_(LOGS_SHEET_NAME, LOGS_COLUMNS, LOGS_HEADER_LABELS);
    return jsonResponse_({ ok: true, habits: habits, logs: logs });
  } catch (err) {
    return jsonResponse_({ ok: false, error: String(err) });
  }
}

/** Finds/creates a sheet and writes the header row if it's brand new. */
function getOrCreateSheet_(name, headerLabels) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
  }
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(headerLabels);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

/**
 * Upserts [rows] (array of plain objects keyed by [columns]) into [sheetName],
 * matching existing rows by the values of [keyColumns] (compared as a joined
 * string) and updating them in place; anything not matched is appended.
 */
function upsertRows_(sheetName, columns, headerLabels, rows, keyColumns) {
  if (!rows || rows.length === 0) return;
  const sheet = getOrCreateSheet_(sheetName, headerLabels);

  const existing = sheet.getDataRange().getValues(); // row 0 is the header
  const keyColumnIndexes = keyColumns.map(function (k) { return columns.indexOf(k); });
  const rowNumberByKey = {};
  for (let r = 1; r < existing.length; r++) {
    const key = keyColumnIndexes.map(function (i) { return existing[r][i]; }).join('|');
    rowNumberByKey[key] = r + 1; // 1-based sheet row number
  }

  rows.forEach(function (obj) {
    const values = columns.map(function (c) { return formatValue_(c, obj[c]); });
    const key = keyColumns.map(function (k) { return obj[k]; }).join('|');
    const existingRowNumber = rowNumberByKey[key];
    if (existingRowNumber) {
      sheet.getRange(existingRowNumber, 1, 1, values.length).setValues([values]);
    } else {
      sheet.appendRow(values);
    }
  });
}

/** Reads a sheet back into an array of plain objects keyed by [columns]. */
function readRows_(sheetName, columns, headerLabels) {
  const sheet = getOrCreateSheet_(sheetName, headerLabels);
  const data = sheet.getDataRange().getValues();
  const result = [];
  for (let r = 1; r < data.length; r++) {
    const obj = {};
    columns.forEach(function (c, i) { obj[c] = parseValue_(c, data[r][i]); });
    result.push(obj);
  }
  return result;
}

function formatValue_(column, value) {
  if (column === 'reminderEnabled' || column === 'completed' || column === 'archived') {
    return value ? 'Yes' : 'No';
  }
  if (column === 'completedAt') {
    return value ? new Date(value) : new Date();
  }
  return value === undefined || value === null ? '' : value;
}

function parseValue_(column, cellValue) {
  if (column === 'reminderEnabled' || column === 'completed' || column === 'archived') {
    return cellValue === 'Yes' || cellValue === true;
  }
  if (column === 'completedAt') {
    return cellValue instanceof Date ? cellValue.getTime() : Number(cellValue) || 0;
  }
  if (column === 'activeDays' || column === 'reminderHour' || column === 'reminderMinute' || column === 'createdAtEpochDay') {
    return Number(cellValue) || 0;
  }
  return cellValue === undefined || cellValue === null ? '' : String(cellValue);
}

function jsonResponse_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
