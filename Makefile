# Détection de l'OS et configuration
ifeq ($(OS),Windows_NT)
    MVN = cmd /c mvnw.cmd
    GRADLEW = gradlew.bat
    # Chemin du SDK (le \: est pour le format local.properties)
    SDK_PATH = C\:/Users/njlou/AppData/Local/Android/Sdk
    FIX_PATH = $(subst /,\,$(1))
    # Suppression de l'espace avant le > pour éviter le "Trailing char"
    CREATE_LOCAL_PROP = if not exist "android\local.properties" (echo sdk.dir=$(SDK_PATH)> android\local.properties)
else
    MVN = ./mvnw
    GRADLEW = ./gradlew
    SDK_PATH = $(HOME)/Android/Sdk
    FIX_PATH = $(1)
    CREATE_LOCAL_PROP = [ ! -f android/local.properties ] && echo "sdk.dir=$(SDK_PATH)" > android/local.properties || true
endif

# Variables
JAR_FILE = target/rondes-nfc.jar
ANDROID_DIR = android

.PHONY: help init run-server run-app install-android clean

help:
	@echo "Commandes disponibles :"
	@echo "  make init            - Installe Maven, configure le SDK et build tout"
	@echo "  make run-app         - Lance le serveur Backend"
	@echo "  make install-android - Installe l'app sur le telephone"

# Initialisation
init:
	@echo "Initialisation du backend..."
	$(MVN) -DskipTests package
	@echo "Configuration du SDK Android..."
	@$(CREATE_LOCAL_PROP)
	@echo "Initialisation du projet Android..."
	cd $(ANDROID_DIR) && $(GRADLEW) assembleDebug

run-server:
	@if not exist $(call FIX_PATH,$(JAR_FILE)) (echo "Erreur: Jar non trouve. Lancez 'make init' d'abord." && exit 1)
	java -jar $(JAR_FILE)

run-app: run-server

install-android:
	@echo "Installation de l'APK sur le telephone..."
	@$(CREATE_LOCAL_PROP)
	cd $(ANDROID_DIR) && $(GRADLEW) assembleDebug
	adb install -r $(ANDROID_DIR)/app/build/outputs/apk/debug/app-debug.apk

clean:
	$(MVN) clean
	cd $(ANDROID_DIR) && $(GRADLEW) clean
