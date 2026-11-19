const { GeminiTranslator } = require('../gemini/translator');

async function testGeminiModule() {
  console.log('--- Testing Gemini Module Architecture ---');
  const translator = new GeminiTranslator('test_key');
  
  if (typeof translator.translate === 'function' && 
      typeof translator.executeVoiceCommand === 'function') {
    console.log('GeminiTranslator class initialized with required methods: PASS');
  } else {
    console.error('GeminiTranslator missing methods: FAIL');
    process.exit(1);
  }

  // Verify missing key exception behavior
  const noKeyTranslator = new GeminiTranslator('');
  try {
    await noKeyTranslator.translate('hello');
    console.error('Expected error on missing key, but passed: FAIL');
    process.exit(1);
  } catch (err) {
    if (err.message.includes('GEMINI_API_KEY_NOT_CONFIGURED')) {
      console.log('Correctly halts on missing API key: PASS');
    }
  }

  console.log('Gemini architecture test completed.\n');
}

if (require.main === module) {
  testGeminiModule();
}

module.exports = { testGeminiModule };
