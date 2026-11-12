/**
 * Smart Farmer Procurement System: sends the app's emails from this Gmail account.
 *
 * The app (GoogleScriptEmailSender.java) POSTs JSON:
 *   { "secret", "to", "subject", "text", "html", "fromName", "inlineImages": { "booking-qr": "<base64 png>" } }
 * Nothing is sent unless "secret" equals the SECRET script property (Project Settings -> Script Properties).
 * The answer is JSON: { "ok": true } or { "ok": false, "error": "unauthorized" | "not_configured" | "bad_request" | "quota" | "send_failed" }.
 *
 * Setup: paste this file, run checkSetup once (it asks for permission to send email), then
 * Deploy -> New deployment -> Web app, Execute as: Me, Who has access: Anyone. Copy the /exec URL.
 */

var MIN_SECRET_LENGTH = 32;
var MAX_BODY_CHARS = 200000;       // a normal email with its QR picture is far smaller

function doGet() {
  return ContentService.createTextOutput('ok');
}

function doPost(e) {
  try {
    var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
    if (!secret || secret.length < MIN_SECRET_LENGTH) {
      return answer_(false, 'not_configured');
    }
    var raw = e && e.postData && e.postData.contents;
    if (!raw || raw.length > MAX_BODY_CHARS) {
      return answer_(false, 'bad_request');
    }
    var req = JSON.parse(raw);
    if (typeof req.secret !== 'string' || !sameText_(req.secret, secret)) {
      return answer_(false, 'unauthorized');
    }
    var to = String(req.to || '');
    // exactly one plain address: no lists, no names, no line breaks
    if (!/^[^\s@,;<>"]+@[^\s@,;<>"]+\.[^\s@,;<>"]+$/.test(to) || !req.subject || !(req.html || req.text)) {
      return answer_(false, 'bad_request');
    }
    if (MailApp.getRemainingDailyQuota() < 1) {
      return answer_(false, 'quota');
    }
    var options = {
      name: String(req.fromName || 'Smart Farmer Procurement System').slice(0, 80),
      htmlBody: req.html ? String(req.html) : undefined
    };
    if (req.inlineImages && typeof req.inlineImages === 'object') {
      options.inlineImages = {};
      Object.keys(req.inlineImages).slice(0, 3).forEach(function (id) {
        options.inlineImages[id] = Utilities.newBlob(Utilities.base64Decode(String(req.inlineImages[id])), 'image/png', id + '.png');
      });
    }
    MailApp.sendEmail(to, String(req.subject).slice(0, 250), String(req.text || ''), options);
    return answer_(true);
  } catch (err) {
    // never echo the request back (it holds the secret)
    return answer_(false, 'send_failed');
  }
}

/** Run once from the editor: asks for the permission to send email and checks the SECRET property. */
function checkSetup() {
  var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
  Logger.log('SECRET is saved and long enough: ' + (!!secret && secret.length >= MIN_SECRET_LENGTH));
  Logger.log('Emails left today: ' + MailApp.getRemainingDailyQuota());
}

function answer_(ok, error) {
  var body = ok ? { ok: true } : { ok: false, error: error };
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

/** Compares in time that does not depend on where the texts differ. */
function sameText_(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  var diff = 0;
  for (var i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}
