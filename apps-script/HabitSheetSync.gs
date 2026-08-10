/**
 * HabitSheetSync.gs
 *
 * Deploy this as a Google Apps Script Web App (Execute as: Me, Access: Anyone with the link).
 * The Android app POSTs JSON like:
 *   { "rows": [ { "habitName": "...", "date": "2026-08-10", "completed": true, "completedAt": 1723... }, ... ] }
 * to the resulting /exec URL, and this script appends one row per entry to the
 * "Habits" sheet of THIS spreadsheet (the one this script is bound to).
 *
 * Full step-by-step setup: /docs/SETUP_GOOGLE_SHEETS.md
 */

const SHEET_NAME = 'Habits';
const HEADERS = ['Date', 'Habit', 'Completed', 'Synced At'];

function doPost(e) {
  try {
    const sheet = getOrCreateSheet_();
    const body = JSON.parse(e.postData.contents || '{}');
    const rows = body.rows || [];

    if (rows.length > 0) {
      const values = rows.map(function (row) {
        return [
          row.date || '',
          row.habitName || '',
          row.completed ? 'Yes' : 'No',
          row.completedAt ? new Date(row.completedAt) : new Date()
        ];
      });
      sheet.getRange(sheet.getLastRow() + 1, 1, values.length, HEADERS.length).setValues(values);
    }

    return jsonResponse_({ ok: true, inserted: rows.length });
  } catch (err) {
    return jsonResponse_({ ok: false, error: String(err) });
  }
}

/** Lets you sanity-check the deployed URL by opening it directly in a browser. */
function doGet(e) {
  return jsonResponse_({ ok: true, message: 'HabitSheetSync is running. POST JSON to this URL to append rows.' });
}

function getOrCreateSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function jsonResponse_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
