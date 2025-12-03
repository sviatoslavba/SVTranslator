package com.example.svtranslator5;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final long DOWNLOAD_TIMEOUT_MS = 10000; // 10 секунд

    // Елементи UI
    private ImageView imageViewOriginal, imageViewResult;
    private TextView tvDetectedText, tvTranslatedText;
    private Button btnSelectImage, btnTranslate, btnSaveImage, btnSaveToTxt, btnSwapLanguages;
    private Spinner spinnerSourceLanguages, spinnerLanguages;

    // Бітмапи
    private Bitmap selectedBitmap, resultBitmap;

    // Тексти
    private String detectedText = "";
    private String translatedText = "";

    // Мови
    private Map<String, String> languageCodeMap;
    private boolean isSourceLanguageSelected = false;

    // Для заміни тексту
    private List<Text.TextBlock> detectedSentences;
    private Map<String, String> sentenceBySentenceTranslation;
    private boolean isSentenceTranslationReady = false;

    // Стейт застосунку
    private boolean isProcessing = false;

    // Таймер для таймауту завантаження
    private CountDownTimer downloadTimer;
    private boolean isDownloadTimedOut = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupLanguageSpinner();
        setupClickListeners();
        requestStoragePermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Зупиняємо таймер при знищенні активності
        if (downloadTimer != null) {
            downloadTimer.cancel();
        }
    }

    private void initViews() {
        imageViewOriginal = findViewById(R.id.imageViewOriginal);
        imageViewResult = findViewById(R.id.imageViewResult);
        tvDetectedText = findViewById(R.id.tvDetectedText);
        tvTranslatedText = findViewById(R.id.tvTranslatedText);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnTranslate = findViewById(R.id.btnTranslate);
        btnSaveImage = findViewById(R.id.btnSaveImage);
        btnSaveToTxt = findViewById(R.id.btnSaveToTxt);
        btnSwapLanguages = findViewById(R.id.btnSwapLanguages);
        spinnerSourceLanguages = findViewById(R.id.spinnerSourceLanguages);
        spinnerLanguages = findViewById(R.id.spinnerLanguages);

        // Спочатку кнопки збереження неактивні
        btnSaveImage.setEnabled(false);
        btnSaveToTxt.setEnabled(false);
    }

    private void setupLanguageSpinner() {
        // Створюємо спільний список мов для обох спінерів
        List<String> languagesList = new ArrayList<>();
        languagesList.add("Оберіть мову");
        languagesList.add("Англійська");
        languagesList.add("Українська");
        languagesList.add("Російська");
        languagesList.add("Польська");
        languagesList.add("Німецька");
        languagesList.add("Французька");
        languagesList.add("Іспанська");
        languagesList.add("Італійська");
        languagesList.add("Албанська");
        languagesList.add("Чеська");

        // Мапа для конвертації назв мов у коди
        languageCodeMap = new HashMap<>();
        languageCodeMap.put("Оберіть мову", "");
        languageCodeMap.put("Англійська", TranslateLanguage.ENGLISH);
        languageCodeMap.put("Українська", TranslateLanguage.UKRAINIAN);
        languageCodeMap.put("Російська", TranslateLanguage.RUSSIAN);
        languageCodeMap.put("Польська", TranslateLanguage.POLISH);
        languageCodeMap.put("Німецька", TranslateLanguage.GERMAN);
        languageCodeMap.put("Французька", TranslateLanguage.FRENCH);
        languageCodeMap.put("Іспанська", TranslateLanguage.SPANISH);
        languageCodeMap.put("Італійська", TranslateLanguage.ITALIAN);
        languageCodeMap.put("Албанська", TranslateLanguage.ALBANIAN);
        languageCodeMap.put("Чеська", TranslateLanguage.CZECH);

        // Спільний адаптер для обох спінерів
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, languagesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Налаштування спінера мови оригіналу
        spinnerSourceLanguages.setAdapter(adapter);
        spinnerSourceLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedKey = parent.getItemAtPosition(position).toString();
                String selectedLanguage = languageCodeMap.get(selectedKey);
                isSourceLanguageSelected = !selectedLanguage.isEmpty();
                updateButtonStates();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                isSourceLanguageSelected = false;
                updateButtonStates();
            }
        });

        // Налаштування спінера мови перекладу
        spinnerLanguages.setAdapter(adapter);
        spinnerLanguages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateButtonStates();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateButtonStates();
            }
        });

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasImage = selectedBitmap != null;
        boolean hasDetectedText = !detectedText.isEmpty();
        boolean hasTranslatedText = !translatedText.isEmpty();
        boolean hasResultImage = resultBitmap != null;

        boolean sourceLangSelected = isSourceLanguageSelected;
        boolean targetLangSelected = spinnerLanguages.getSelectedItemPosition() > 0;

        // Кнопка вибору зображення завжди активна
        btnSelectImage.setEnabled(true);

        // Кнопка перекладу активна, коли є розпізнаний текст і обрані обидві мови
        btnTranslate.setEnabled(hasDetectedText && sourceLangSelected && targetLangSelected && !isProcessing);

        // Кнопка обміну місцями активна, коли обрані обидві мови
        btnSwapLanguages.setEnabled(sourceLangSelected && targetLangSelected);

        // Кнопки збереження активні, коли є відповідний контент
        btnSaveImage.setEnabled(hasResultImage);
        btnSaveToTxt.setEnabled(hasTranslatedText);

        // Візуальні індикації
        btnTranslate.setAlpha((hasDetectedText && sourceLangSelected && targetLangSelected && !isProcessing) ? 1.0f : 0.5f);
        btnSwapLanguages.setAlpha((sourceLangSelected && targetLangSelected) ? 1.0f : 0.5f);
        btnSaveImage.setAlpha(hasResultImage ? 1.0f : 0.5f);
        btnSaveToTxt.setAlpha(hasTranslatedText ? 1.0f : 0.5f);
    }

    private void setupClickListeners() {
        // Кнопка вибору зображення
        btnSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectImage();
            }
        });

        // Кнопка перекладу
        btnTranslate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!detectedText.isEmpty()) {
                    translateText();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Спочатку розпізнайте текст", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Кнопка обміну місцями
        btnSwapLanguages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swapLanguages();
            }
        });

        // Кнопка збереження зображення
        btnSaveImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resultBitmap != null) {
                    saveResultImage();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Немає зображення для збереження", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Кнопка збереження тексту
        btnSaveToTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!translatedText.isEmpty()) {
                    saveToTxtFile();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Немає тексту для збереження", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Перевірка наявності мережі
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    // Обмін місцями мов
    private void swapLanguages() {
        int sourcePos = spinnerSourceLanguages.getSelectedItemPosition();
        int targetPos = spinnerLanguages.getSelectedItemPosition();

        // Перевіряємо, що обрані дійсні мови (не "Оберіть мову")
        if (sourcePos > 0 && targetPos > 0) {
            // Міняємо місцями позиції в спінерах
            spinnerSourceLanguages.setSelection(targetPos);
            spinnerLanguages.setSelection(sourcePos);

            Toast.makeText(this, "Мови замінено місцями", Toast.LENGTH_SHORT).show();
        } else if (sourcePos == 0 || targetPos == 0) {
            Toast.makeText(this, "Оберіть обидві мови", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void recognizeTextFromImage(Bitmap bitmap) {
        if (bitmap == null) return;

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        tvDetectedText.setText("Розпізнавання тексту...");

        recognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        detectedText = text.getText();
                        detectedSentences = new ArrayList<>();

                        // Зберігаємо всі блоки тексту як речення
                        for (Text.TextBlock textBlock : text.getTextBlocks()) {
                            detectedSentences.add(textBlock);
                        }

                        tvDetectedText.setText(detectedText);
                        Toast.makeText(MainActivity.this,
                                "Текст розпізнано!", Toast.LENGTH_SHORT).show();
                        updateButtonStates();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        tvDetectedText.setText("Помилка розпізнавання: " + e.getMessage());
                        detectedText = "";
                        detectedSentences = null;
                        Toast.makeText(MainActivity.this,
                                "Не вдалося розпізнати текст", Toast.LENGTH_SHORT).show();
                        updateButtonStates();
                    }
                });
    }

    private void translateText() {
        if (detectedText.isEmpty()) {
            Toast.makeText(this, "Спочатку розпізнайте текст", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spinnerLanguages.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Оберіть мову для перекладу", Toast.LENGTH_SHORT).show();
            return;
        }

        // Показуємо інформацію про мережу
        if (!isNetworkAvailable()) {
            Toast.makeText(this,
                    "Відсутнє підключення до інтернету. Буде використано офлайн модель, якщо вона завантажена.",
                    Toast.LENGTH_LONG).show();
        }

        final String sourceLanguage = spinnerSourceLanguages.getSelectedItem().toString();
        final String targetLanguage = spinnerLanguages.getSelectedItem().toString();

        isProcessing = true;
        updateButtonStates();
        performTranslation(sourceLanguage, targetLanguage);
    }

    private void performTranslation(String sourceLanguage, String targetLanguage) {
        // Конвертуємо назви мов у коди
        String sourceCode = languageCodeMap.get(sourceLanguage);
        String targetCode = languageCodeMap.get(targetLanguage);

        if (sourceCode == null || targetCode == null || sourceCode.isEmpty() || targetCode.isEmpty()) {
            Toast.makeText(this, "Невірно обрані мови", Toast.LENGTH_SHORT).show();
            isProcessing = false;
            updateButtonStates();
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build();

        final Translator translator = Translation.getClient(options);
        tvTranslatedText.setText("Завантаження моделі перекладу...");

        // Скидаємо прапорець таймауту
        isDownloadTimedOut = false;

        // Запускаємо таймер для таймауту завантаження
        startDownloadTimer();

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi() // Опціонально: вимагає Wi-Fi для завантаження
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        // Зупиняємо таймер
                        stopDownloadTimer();

                        // Перевіряємо, чи не був таймаут
                        if (isDownloadTimedOut) {
                            Log.d("Translation", "Завантаження моделі було перервано через таймаут");
                            return;
                        }

                        // Модель завантажена або вже є офлайн
                        tvTranslatedText.setText("Переклад...");

                        translator.translate(detectedText)
                                .addOnSuccessListener(new OnSuccessListener<String>() {
                                    @Override
                                    public void onSuccess(String s) {
                                        translatedText = s;
                                        tvTranslatedText.setText(translatedText);
                                        Toast.makeText(MainActivity.this,
                                                "Текст перекладено!", Toast.LENGTH_SHORT).show();

                                        // Автоматично запускаємо заміну тексту на зображенні
                                        if (detectedSentences != null) {
                                            translateTextSentenceBySentence();
                                        } else {
                                            // Якщо немає позицій, все одно створюємо зображення
                                            replaceTextOnImage();
                                        }

                                        isProcessing = false;
                                        updateButtonStates();
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        tvTranslatedText.setText("Помилка перекладу: " + e.getMessage());
                                        Toast.makeText(MainActivity.this,
                                                "Не вдалося перекласти текст. Спробуйте ще раз.",
                                                Toast.LENGTH_LONG).show();
                                        isProcessing = false;
                                        updateButtonStates();
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Зупиняємо таймер
                        stopDownloadTimer();

                        // Перевіряємо, чи не був таймаут
                        if (isDownloadTimedOut) {
                            return; // Вже обробили таймаут
                        }

                        // Помилка завантаження моделі (немає мережі або інша помилка)
                        tvTranslatedText.setText("Помилка завантаження моделі");

                        String errorMessage = e.getMessage();
                        if (errorMessage != null) {
                            if (errorMessage.toLowerCase().contains("network") ||
                                    errorMessage.toLowerCase().contains("connection") ||
                                    errorMessage.toLowerCase().contains("unavailable")) {
                                Toast.makeText(MainActivity.this,
                                        "Не вдалося завантажити мовну модель. Перевірте підключення до інтернету.",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(MainActivity.this,
                                        "Помилка завантаження моделі перекладу: " + errorMessage,
                                        Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Не вдалося завантажити мовну модель. Перевірте підключення до інтернету.",
                                    Toast.LENGTH_LONG).show();
                        }

                        isProcessing = false;
                        updateButtonStates();
                    }
                });
    }

    // Запуск таймера для завантаження моделі
    private void startDownloadTimer() {
        downloadTimer = new CountDownTimer(DOWNLOAD_TIMEOUT_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Можна показати прогрес, якщо потрібно
                Log.d("DownloadTimer", "Залишилось: " + (millisUntilFinished / 1000) + " секунд");
            }

            @Override
            public void onFinish() {
                isDownloadTimedOut = true;
                tvTranslatedText.setText("Таймаут завантаження");
                Toast.makeText(MainActivity.this,
                        "Завантаження моделі зайняло занадто багато часу. Перевірте підключення до інтернету.",
                        Toast.LENGTH_LONG).show();

                isProcessing = false;
                updateButtonStates();
            }
        }.start();
    }

    // Зупинка таймера
    private void stopDownloadTimer() {
        if (downloadTimer != null) {
            downloadTimer.cancel();
            downloadTimer = null;
        }
    }

    private void translateTextSentenceBySentence() {
        if (detectedSentences == null || detectedSentences.isEmpty()) {
            replaceTextOnImage();
            return;
        }

        final String sourceLanguage = spinnerSourceLanguages.getSelectedItem().toString();
        final String targetLanguage = spinnerLanguages.getSelectedItem().toString();

        String sourceCode = languageCodeMap.get(sourceLanguage);
        String targetCode = languageCodeMap.get(targetLanguage);

        if (sourceCode == null || targetCode == null || sourceCode.isEmpty() || targetCode.isEmpty()) {
            replaceTextOnImage();
            return;
        }

        sentenceBySentenceTranslation = new HashMap<>();
        isSentenceTranslationReady = false;

        List<String> sentencesToTranslate = new ArrayList<>();
        Map<String, List<Text.TextBlock>> sentencePositions = new HashMap<>();

        for (Text.TextBlock textBlock : detectedSentences) {
            String sentence = textBlock.getText().trim();
            if (!sentence.isEmpty() && sentence.length() > 3) {
                sentencesToTranslate.add(sentence);
                if (!sentencePositions.containsKey(sentence)) {
                    sentencePositions.put(sentence, new ArrayList<>());
                }
                sentencePositions.get(sentence).add(textBlock);
            }
        }

        if (sentencesToTranslate.isEmpty()) {
            replaceTextOnImage();
            return;
        }

        translateSentencesSequentially(sentencesToTranslate, sentencePositions, sourceCode, targetCode, 0);
    }

    private void translateSentencesSequentially(List<String> sentences, Map<String, List<Text.TextBlock>> sentencePositions,
                                                String sourceLanguage, String targetLanguage, int index) {
        if (index >= sentences.size()) {
            isSentenceTranslationReady = true;
            replaceTextOnImage();
            return;
        }

        String sentence = sentences.get(index);

        if (sentence.length() <= 3) {
            sentenceBySentenceTranslation.put(sentence, sentence);
            translateSentencesSequentially(sentences, sentencePositions, sourceLanguage, targetLanguage, index + 1);
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build();

        final Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        translator.translate(sentence)
                                .addOnSuccessListener(new OnSuccessListener<String>() {
                                    @Override
                                    public void onSuccess(String translatedSentence) {
                                        sentenceBySentenceTranslation.put(sentence, translatedSentence);
                                        translateSentencesSequentially(sentences, sentencePositions, sourceLanguage, targetLanguage, index + 1);
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        sentenceBySentenceTranslation.put(sentence, sentence);
                                        translateSentencesSequentially(sentences, sentencePositions, sourceLanguage, targetLanguage, index + 1);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Модель не завантажена - пропускаємо переклад цього речення
                        sentenceBySentenceTranslation.put(sentence, sentence);
                        translateSentencesSequentially(sentences, sentencePositions, sourceLanguage, targetLanguage, index + 1);
                    }
                });
    }

    private void replaceTextOnImage() {
        if (selectedBitmap == null || translatedText.isEmpty()) {
            Toast.makeText(this, "Немає даних для створення зображення", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (isSentenceTranslationReady && sentenceBySentenceTranslation != null && detectedSentences != null) {
                // Заміна по реченнях
                resultBitmap = selectedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(resultBitmap);

                Paint erasePaint = new Paint();
                erasePaint.setColor(Color.WHITE);
                erasePaint.setStyle(Paint.Style.FILL);
                erasePaint.setAntiAlias(true);

                Paint textPaint = new Paint();
                textPaint.setColor(Color.BLACK);
                textPaint.setAntiAlias(true);
                textPaint.setStyle(Paint.Style.FILL);

                for (Text.TextBlock textBlock : detectedSentences) {
                    String originalSentence = textBlock.getText().trim();
                    String translatedSentence = sentenceBySentenceTranslation.get(originalSentence);

                    if (translatedSentence != null && !translatedSentence.equals(originalSentence)) {
                        Rect boundingBox = textBlock.getBoundingBox();
                        if (boundingBox != null) {
                            canvas.drawRect(boundingBox, erasePaint);

                            float originalFontSize = estimateOriginalFontSize(textBlock);
                            textPaint.setTextSize(originalFontSize);

                            List<Text.Line> originalLines = getLinesFromTextBlock(textBlock);
                            List<String> translatedLines = adaptTranslationToOriginalStructure(
                                    translatedSentence, originalLines, textPaint, boundingBox.width());

                            drawTextPreservingStructure(canvas, textPaint, translatedLines, originalLines, boundingBox);
                        }
                    }
                }
            } else {
                // Проста заміна - текст внизу зображення
                resultBitmap = selectedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(resultBitmap);
                Paint paint = new Paint();

                paint.setColor(Color.BLACK);
                paint.setTextSize(40);
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL);
                paint.setShadowLayer(4, 2, 2, Color.WHITE);

                List<String> lines = splitTextIntoLines(translatedText, paint, resultBitmap.getWidth() - 100);

                int lineHeight = 50;
                int padding = 20;
                int backgroundHeight = lines.size() * lineHeight + padding * 2;
                int backgroundWidth = resultBitmap.getWidth() - 100;
                int backgroundTop = resultBitmap.getHeight() - backgroundHeight - 50;
                int backgroundLeft = 50;

                Paint backgroundPaint = new Paint();
                backgroundPaint.setColor(Color.argb(200, 255, 255, 255));
                backgroundPaint.setStyle(Paint.Style.FILL);
                backgroundPaint.setAntiAlias(true);

                canvas.drawRoundRect(
                        backgroundLeft,
                        backgroundTop,
                        backgroundLeft + backgroundWidth,
                        backgroundTop + backgroundHeight,
                        20,
                        20,
                        backgroundPaint
                );

                Paint borderPaint = new Paint();
                borderPaint.setColor(Color.BLACK);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(2);
                borderPaint.setAntiAlias(true);

                canvas.drawRoundRect(
                        backgroundLeft,
                        backgroundTop,
                        backgroundLeft + backgroundWidth,
                        backgroundTop + backgroundHeight,
                        20,
                        20,
                        borderPaint
                );

                int textX = backgroundLeft + padding;
                int textY = backgroundTop + padding + 30;

                for (int i = 0; i < lines.size(); i++) {
                    canvas.drawText(lines.get(i), textX, textY + i * lineHeight, paint);
                }
            }

            imageViewResult.setImageBitmap(resultBitmap);
            imageViewResult.setVisibility(View.VISIBLE);
            btnSaveImage.setEnabled(true);
            updateButtonStates();

        } catch (Exception e) {
            Toast.makeText(this, "Помилка при створенні зображення: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("ReplaceText", "Помилка: " + e.getMessage());
        }
    }

    // Допоміжні методи для заміни тексту
    private float estimateOriginalFontSize(Text.TextBlock textBlock) {
        float totalHeight = 0;
        int lineCount = 0;

        for (Text.Line line : textBlock.getLines()) {
            Rect boundingBox = line.getBoundingBox();
            if (boundingBox != null) {
                float lineHeight = boundingBox.height() * 0.7f;
                totalHeight += lineHeight;
                lineCount++;
            }
        }

        if (lineCount > 0) {
            return totalHeight / lineCount;
        }

        return 24f;
    }

    private List<Text.Line> getLinesFromTextBlock(Text.TextBlock textBlock) {
        List<Text.Line> lines = new ArrayList<>();
        for (Text.Line line : textBlock.getLines()) {
            lines.add(line);
        }
        return lines;
    }

    private List<String> adaptTranslationToOriginalStructure(String translatedText,
                                                             List<Text.Line> originalLines,
                                                             Paint paint, float maxWidth) {
        List<String> resultLines = new ArrayList<>();

        if (originalLines.isEmpty()) {
            return splitTextIntoLines(translatedText, paint, maxWidth);
        }

        String[] words = translatedText.split("\\s+");
        int currentWordIndex = 0;

        for (Text.Line originalLine : originalLines) {
            if (currentWordIndex >= words.length) {
                break;
            }

            StringBuilder currentLine = new StringBuilder();
            Rect lineBoundingBox = originalLine.getBoundingBox();
            float availableWidth = lineBoundingBox != null ? lineBoundingBox.width() : maxWidth;

            int originalWordCount = countWordsInLine(originalLine);

            for (int i = 0; i < originalWordCount && currentWordIndex < words.length; i++) {
                String testWord = words[currentWordIndex];
                String testLine = currentLine.length() > 0 ?
                        currentLine.toString() + " " + testWord : testWord;

                if (paint.measureText(testLine) <= availableWidth) {
                    currentLine.append(currentLine.length() > 0 ? " " : "").append(testWord);
                    currentWordIndex++;
                } else {
                    break;
                }
            }

            if (currentLine.length() > 0) {
                resultLines.add(currentLine.toString());
            }
        }

        if (currentWordIndex < words.length) {
            StringBuilder remainingText = new StringBuilder();
            for (int i = currentWordIndex; i < words.length; i++) {
                remainingText.append(remainingText.length() > 0 ? " " : "").append(words[i]);
            }
            List<String> additionalLines = splitTextIntoLines(remainingText.toString(), paint, maxWidth);
            resultLines.addAll(additionalLines);
        }

        return resultLines;
    }

    private int countWordsInLine(Text.Line line) {
        String lineText = line.getText().trim();
        if (lineText.isEmpty()) return 0;
        return lineText.split("\\s+").length;
    }

    private void drawTextPreservingStructure(Canvas canvas, Paint paint,
                                             List<String> translatedLines,
                                             List<Text.Line> originalLines,
                                             Rect textBlockBoundingBox) {
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float lineHeight = fontMetrics.descent - fontMetrics.ascent;

        for (int i = 0; i < translatedLines.size(); i++) {
            if (i >= originalLines.size()) {
                break;
            }

            Text.Line originalLine = originalLines.get(i);
            Rect lineBoundingBox = originalLine.getBoundingBox();
            if (lineBoundingBox == null) continue;

            String lineText = translatedLines.get(i);
            float x = lineBoundingBox.left;
            float y = lineBoundingBox.top + (lineBoundingBox.height() - fontMetrics.ascent - fontMetrics.descent) / 2;
            canvas.drawText(lineText, x, y, paint);
        }
    }

    private List<String> splitTextIntoLines(String text, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() > 0 ?
                    currentLine.toString() + " " + word : word;
            float lineWidth = paint.measureText(testLine);

            if (lineWidth <= maxWidth) {
                currentLine.append(currentLine.length() > 0 ? " " : "").append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.addAll(splitLongWord(word, paint, maxWidth));
                    currentLine = new StringBuilder();
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private List<String> splitLongWord(String word, Paint paint, float maxWidth) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();

        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            String testPart = currentPart.toString() + c;
            float partWidth = paint.measureText(testPart);

            if (partWidth <= maxWidth) {
                currentPart.append(c);
            } else {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                    currentPart = new StringBuilder(String.valueOf(c));
                } else {
                    parts.add(String.valueOf(c));
                }
            }
        }

        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }

        return parts;
    }

    private void saveResultImage() {
        if (resultBitmap == null) {
            Toast.makeText(this, "Немає зображення для збереження", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "translated_image_" + timeStamp + ".png";

            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File appDir = new File(downloadsDir, "SVTranslator");

            if (!appDir.exists()) {
                appDir.mkdirs();
            }

            File file = new File(appDir, fileName);

            FileOutputStream outputStream = new FileOutputStream(file);
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            Toast.makeText(this, "Зображення збережено у папці SVTranslator", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Toast.makeText(this, "Помилка збереження зображення: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToTxtFile() {
        if (translatedText.isEmpty()) {
            Toast.makeText(this, "Немає тексту для збереження", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "translated_text_" + timeStamp + ".txt";

            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File appDir = new File(downloadsDir, "SVTranslator");

            if (!appDir.exists()) {
                appDir.mkdirs();
            }

            File file = new File(appDir, fileName);

            FileWriter writer = new FileWriter(file);
            writer.write("Оригінальний текст:\n" + detectedText + "\n\nПереклад:\n" + translatedText);
            writer.flush();
            writer.close();

            Toast.makeText(this, "Текст збережено у папці SVTranslator", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Toast.makeText(this, "Помилка збереження: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Дозвіл на збереження файлів не надано", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                selectedBitmap = BitmapFactory.decodeStream(inputStream);
                imageViewOriginal.setImageBitmap(selectedBitmap);

                // Очищаємо попередні результати
                detectedText = "";
                translatedText = "";
                resultBitmap = null;
                detectedSentences = null;
                sentenceBySentenceTranslation = null;
                isSentenceTranslationReady = false;
                isProcessing = false;
                isDownloadTimedOut = false;

                // Зупиняємо таймер, якщо він працює
                stopDownloadTimer();

                tvDetectedText.setText("");
                tvTranslatedText.setText("");
                imageViewResult.setVisibility(View.GONE);
                imageViewResult.setImageBitmap(null);

                // Скидаємо кнопки збереження
                btnSaveImage.setEnabled(false);
                btnSaveToTxt.setEnabled(false);

                updateButtonStates();

                // Автоматично запускаємо розпізнавання тексту
                recognizeTextFromImage(selectedBitmap);

            } catch (Exception e) {
                Toast.makeText(this, "Помилка завантаження зображення", Toast.LENGTH_SHORT).show();
            }
        }
    }
}