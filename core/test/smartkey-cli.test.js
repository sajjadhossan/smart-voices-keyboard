const test = require('node:test');
const assert = require('node:assert');
const {
  handlePredict,
  handlePhonetic,
  handleCommand,
  handleClip,
  handleStatus,
  parseFlags
} = require('../../cli/smartkey');

test('CLI-Anything Smartkey Harness - Unit Tests', async (t) => {

  await t.test('parseFlags should parse arguments and flags correctly', () => {
    const raw = ['predict', 'চাল', '--json', '--limit', '8', '--context', 'আমি', '--mode', 'bn'];
    const { flags, positionals } = parseFlags(raw);
    assert.strictEqual(flags.json, true);
    assert.strictEqual(flags.limit, 8);
    assert.strictEqual(flags.context, 'আমি');
    assert.strictEqual(flags.mode, 'bn');
    assert.deepStrictEqual(positionals, ['predict', 'চাল']);
  });

  await t.test('handlePredict should return compound expansions and alternates', async () => {
    // 1. Compound word expansion: 'চাল' -> 'চালতা', 'চালাক', 'চালানো'
    const resChal = await handlePredict(['চাল'], { mode: 'bn', limit: 12, context: '' });
    assert.strictEqual(resChal.success, true);
    assert(resChal.data.predictions.includes('চাল'));
    assert(resChal.data.predictions.includes('চালতা'));
    assert(resChal.data.predictions.includes('চালাক'));

    // 2. Alternate phonetic prefix: 'তা' -> 'টা'
    const resTa = await handlePredict(['তা'], { mode: 'bn', limit: 12, context: '' });
    assert.strictEqual(resTa.success, true);
    assert(resTa.data.predictions.includes('তা'));
    assert(resTa.data.predictions.includes('টা'));
  });

  await t.test('handlePhonetic should accurately transliterate Romanized text', async () => {
    const res = await handlePhonetic(['dhonnobad']);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.output, 'ধন্যবাদ');

    const res2 = await handlePhonetic(['ami', 'bhalobashi']);
    assert.strictEqual(res2.success, true);
    assert.strictEqual(res2.data.output, 'আমি ভালোবাসি');
  });

  await t.test('handleCommand should parse intent and extract payload', async () => {
    const res = await handleCommand(['বাংলা করো:', 'How', 'are', 'you']);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.intent, 'TRANSLATE_TO_BN');
    assert.strictEqual(res.data.payload, 'How are you');

    const resEmail = await handleCommand(['ফর্মাল ইমেইল বানাও: ছুটির আবেদন']);
    assert.strictEqual(resEmail.success, true);
    assert.strictEqual(resEmail.data.intent, 'FORMAL_EMAIL');
  });

  await t.test('handleClip should add and list clipboard items', async () => {
    const addRes = await handleClip(['add', 'CLI Test Clip Text'], { limit: 10 });
    assert.strictEqual(addRes.success, true);
    assert.strictEqual(addRes.data.text, 'CLI Test Clip Text');

    const listRes = await handleClip(['list'], { limit: 5 });
    assert.strictEqual(listRes.success, true);
    assert(Array.isArray(listRes.data.clips));
    assert(listRes.data.clips.some(c => c.text === 'CLI Test Clip Text'));
  });

  await t.test('handleStatus should return accurate platform and version metadata', () => {
    const res = handleStatus();
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.name, 'smart-voices-keyboard');
    assert.strictEqual(res.data.version, '2.3.1');
    assert(res.data.supportedPlatforms.includes('android'));
    assert(res.data.supportedPlatforms.includes('windows'));
  });

});
