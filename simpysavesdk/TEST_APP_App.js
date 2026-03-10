/**
 * Copy this file to your test app as App.js (or merge into App.tsx).
 * Requires: npm install /path/to/simpysavesdk, RECORD_AUDIO in AndroidManifest.
 */
import React, { useState } from 'react';
import { Button, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import SimplySaveSDK, { Language } from '@nippon/simplysave-voice-sdk';

const sdk = SimplySaveSDK.getInstance();
// Minimal dev JWT (packageName + cert fingerprint + exp). Replace for production.
const LICENSE = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJwYWNrYWdlTmFtZSI6ImNvbS5leGFtcGxlLnRlc3QiLCJjZXJ0aWZpY2F0ZUZpbmdlcnByaW50IjoiYWJjZGVmIiwiZXhwIjoxOTk5OTk5OTk5fQ.x';

export default function App() {
  const [input, setInput] = useState('balance check karna hai');
  const [log, setLog] = useState('');
  const [ready, setReady] = useState(false);

  const init = async () => {
    try {
      setLog('Initializing SDK...');
      await sdk.initialize(LICENSE, {
        targetLanguage: Language.HINDI,
        modelDownloadBaseUrl: 'http://10.0.2.2:8765/release', // emulator; use your PC IP for physical device
      });
      setReady(true);
      setLog('SDK ready. Tap "Process Text" to test.');
    } catch (e) {
      setLog('Init failed: ' + (e?.message || String(e)));
    }
  };

  const processText = async () => {
    if (!ready) {
      setLog('Initialize first.');
      return;
    }
    try {
      setLog('Processing...');
      const result = await sdk.processText(input, 'session-1', Language.HINDI);
      setLog(
        'Intent: ' + (result?.intent ?? '') + '\n' +
        'Response (target): ' + (result?.responseTextTarget ?? '') + '\n' +
        'Complete: ' + (result?.isComplete ?? false)
      );
    } catch (e) {
      setLog('Process failed: ' + (e?.message || String(e)));
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Simply Save SDK Test</Text>
      <TextInput
        style={styles.input}
        value={input}
        onChangeText={setInput}
        placeholder="Enter Hindi/English text"
      />
      <View style={styles.row}>
        <Button title="Initialize SDK" onPress={init} />
        <Button title="Process Text" onPress={processText} disabled={!ready} />
      </View>
      <ScrollView style={styles.log}>
        <Text style={styles.logText}>{log || 'Tap Initialize, then Process Text.'}</Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, paddingTop: 60 },
  title: { fontSize: 18, marginBottom: 12 },
  input: { borderWidth: 1, padding: 8, marginBottom: 12 },
  row: { flexDirection: 'row', gap: 8, marginBottom: 12 },
  log: { flex: 1, backgroundColor: '#f0f0f0', padding: 8 },
  logText: { fontFamily: 'monospace', fontSize: 12 },
});
