@echo off
chcp 65001 >nul
echo ======================================================================
echo           STUDYAI - KHOI DONG TOAN BO HE THONG SERVER
echo ======================================================================
echo.

echo [*] 1. Dang khoi dong Backend API (Laravel - Port 8000)...
start "StudyAI - Laravel API (Port 8000)" cmd /k "cd /d c:\Users\Nguyen\AndroidStudioProjects\StudyAI\backend && php artisan serve --host=0.0.0.0 --port=8000"

echo [*] 2. Dang khoi dong AI Microservice (FastAPI - Port 8001)...
start "StudyAI - FastAPI AI Service (Port 8001)" cmd /k "cd /d c:\Users\Nguyen\AndroidStudioProjects\StudyAI\ai-service && python -m uvicorn main:app --host=0.0.0.0 --port=8001"

echo [*] 3. Dang khoi dong Laravel Queue Worker (Xu ly bai giang chay ngam)...
start "StudyAI - Queue Worker" cmd /k "cd /d c:\Users\Nguyen\AndroidStudioProjects\StudyAI\backend && php artisan queue:work --timeout=1800 --tries=2 --sleep=2"

echo [*] 4. Dang khoi dong Ngrok Tunnel (Ket noi tu dien thoai that)...
start "StudyAI - Ngrok Tunnel (Port 8000)" cmd /k "ngrok http 8000"

echo.
echo ======================================================================
echo [OK] DA KHOI DONG XONG TAT CA CAC DICH VU!
echo.
echo 1. Laravel API:    http://127.0.0.1:8000
echo 2. FastAPI AI:     http://127.0.0.1:8001
echo 3. Queue Worker:   Chay ngam xu ly ASR & Note
echo 4. Ngrok Tunnel:   https://unripened-un-nonorientable.ngrok-free.dev
echo.
echo Bay gio ban co the mo ung dung StudyAI tren dien thoai that hoac may ao!
echo ======================================================================
pause
