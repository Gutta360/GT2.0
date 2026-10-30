const [webSocketUrl, action] = process.argv.slice(2);
if (!webSocketUrl || !action) throw new Error('Usage: node scripts/android-emulator-e2e.mjs <webSocketUrl> <login|openRegistration|openDetails|selectFirst|openEdit|register|edit|sync|logout|protectedHome|databaseCounts|inspect|controls>');

const testUsername = JSON.stringify(process.env.GT2_TEST_USER ?? '');
const testPassword = JSON.stringify(process.env.GT2_TEST_PASSWORD ?? '');

const expressions = {
  login: `(() => {
    const set = (selector, value) => {
      const element = document.querySelector(selector);
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, value);
      element.dispatchEvent(new Event('input', { bubbles: true }));
    };
    set('input[name="username"]', ${testUsername}); set('input[name="password"]', ${testPassword});
    document.querySelector('form').requestSubmit(); return 'login submitted';
  })()`,
  openRegistration: `(async () => {
    document.querySelector('.nav-dropdown button').click();
    await new Promise(resolve => setTimeout(resolve, 100));
    document.querySelector('a[href="/patients/registration"]').click();
    return 'registration opened';
  })()`,
  openDetails: `(async () => {
    document.querySelector('.nav-dropdown button').click();
    await new Promise(resolve => setTimeout(resolve, 100));
    document.querySelector('a[href="/patients/details"]').click();
    return 'details opened';
  })()`,
  selectFirst: `(async () => {
    const result = document.querySelector('.patient-list button');
    if (!result) throw new Error('No patient result is available');
    result.click(); await new Promise(resolve => setTimeout(resolve, 500));
    return 'first patient selected';
  })()`,
  openEdit: `(async () => {
    const edit = document.querySelector('.patient-detail .secondary-button');
    if (!edit) throw new Error('Patient edit action is unavailable');
    edit.click(); await new Promise(resolve => setTimeout(resolve, 500));
    return 'patient edit opened';
  })()`,
  register: `(() => {
    const set = (name, value) => {
      const element = document.querySelector('[formcontrolname="' + name + '"]');
      if (!element) throw new Error('Missing form control: ' + name);
      const prototype = element instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value);
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
    };
    set('firstName', 'Synthetic'); set('lastName', 'Patient'); set('age', '30');
    set('mobile', '9999999999'); set('address', 'Offline QA Record'); set('consultationFee', '100');
    Array.from(document.querySelectorAll('[formcontrolname="gender"]')).find(element => element.value === 'OTHER').click();
    Array.from(document.querySelectorAll('[formcontrolname="referredBy"]')).find(element => element.value === 'SELF').click();
    document.querySelector('form').requestSubmit();
    return 'registration submitted';
  })()`,
  edit: `(() => {
    const element = document.querySelector('[formcontrolname="address"]');
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(element, 'Offline QA Record Updated');
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    document.querySelector('form').requestSubmit();
    return 'update submitted';
  })()`,
  databaseCounts: `(async () => {
    const sqlite = window.Capacitor.Plugins.CapacitorSQLite;
    const patients = await sqlite.query({ database: 'gt2_operational', statement: 'SELECT COUNT(*) AS count FROM patients', values: [], readonly: false });
    const outbox = await sqlite.query({ database: 'gt2_operational', statement: 'SELECT COUNT(*) AS count FROM sync_outbox', values: [], readonly: false });
    return { patients: patients.values[0].count, outbox: outbox.values[0].count };
  })()`,
  sync: `(async () => {
    document.querySelector('button[aria-label="Sync"]').click();
    await new Promise(resolve => setTimeout(resolve, 100));
    return document.querySelector('[role="status"]')?.textContent?.trim();
  })()`,
  logout: `(async () => {
    document.querySelector('button[aria-label="Logout"]').click();
    await new Promise(resolve => setTimeout(resolve, 500));
    return location.pathname;
  })()`,
  protectedHome: `(async () => {
    history.pushState({}, '', '/home');
    window.dispatchEvent(new PopStateEvent('popstate'));
    await new Promise(resolve => setTimeout(resolve, 500));
    return location.pathname;
  })()`,
  inspect: `({ url: location.pathname, text: document.body.innerText.slice(0, 1200) })`,
  controls: `Array.from(document.querySelectorAll('input, textarea')).map(element => ({ name: element.getAttribute('formcontrolname'), value: element.value, type: element.type }))`
};

const expression = expressions[action];
if (!expression) throw new Error(`Unknown action: ${action}`);

const socket = new WebSocket(webSocketUrl);
const result = await new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('DevTools request timed out')), 10_000);
  socket.addEventListener('open', () => socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } })));
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id !== 1) return;
    clearTimeout(timer); resolve(message.result);
  });
  socket.addEventListener('error', reject);
});
socket.close();
if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
console.log(result.result?.value ?? 'ok');
