const { PhoneticEngine } = require('../phonetic/phonetic-engine');

function runTests() {
  console.log('--- Testing Phonetic Engine ---');

  const testCases = [
    { input: 'ami bangla bhalobashi', expectedSub: 'আমি বাংলা ভালোবাসি' },
    { input: 'dhonnobad', expectedSub: 'ধন্যবাদ' },
    { input: 'kemon acho bondhu?', expectedSub: 'কেমন আছো' },
    { input: 'shonar bangladesh', expectedSub: 'সোনার বাংলাদেশ' }
  ];

  let passed = 0;
  for (const tc of testCases) {
    const output = PhoneticEngine.transliterateText(tc.input);
    const ok = output.includes(tc.expectedSub) || output === tc.expectedSub;
    console.log(`Input: "${tc.input}" => Output: "${output}" [${ok ? 'PASS' : 'FAIL'}]`);
    if (ok) passed++;
  }

  console.log(`Summary: ${passed}/${testCases.length} tests passed.\n`);
  return passed === testCases.length;
}

if (require.main === module) {
  const success = runTests();
  process.exit(success ? 0 : 1);
}

module.exports = { runTests };
