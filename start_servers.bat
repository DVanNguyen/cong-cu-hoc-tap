@echo off
chcp 65001 >nul
set "ROOT_DIR=%~dp0"

echo ======================================================================
echo           STUDYAI v2.0 - KHOI DONG TOAN BO HE THONG SERVER
echo ======================================================================
echo [*] Thu muc goc du an: %ROOT_DIR%
echo.

echo [*] 1. Khoi dong Backend API (Laravel - Port 8000)...
start "StudyAI - Laravel API" cmd /k "cd /d "%ROOT_DIR%backend" && php artisan serve --host=0.0.0.0 --port=8000"

echo [*] 2. Khoi dong AI Microservice (FastAPI - Port 8001)...
start "StudyAI - FastAPI AI" cmd /k "cd /d "%ROOT_DIR%ai-service" && python -m uvicorn main:app --host 0.0.0.0 --port 8001"

echo [*] 3. Khoi dong Laravel Queue Worker...
start "StudyAI - Queue Worker" cmd /k "cd /d "%ROOT_DIR%backend" && php artisan queue:work --timeout=1800 --tries=2 --sleep=2"

echo [*] 4. Khoi dong Ngrok Tunnel (Port 8000)...
start "StudyAI - Ngrok Tunnel" cmd /k "ngrok http --url=unripened-un-nonorientable.ngrok-free.dev 8000 || ngrok http 8000"

echo.
echo [*] Dang cho 6 giay de cac dich vu khoi dong va kiem tra Health...
timeout /t 6 /nobreak >nul

echo [*] Kiem tra suc khoe he thong:
curl.exe -s http://127.0.0.1:8001/health && echo. || echo [WARN] FastAPI AI (8001) dang nap model, vui long cho them it giay...
curl.exe -s -o nul -w "[HTTP %%{http_code}]" http://127.0.0.1:8000/api/v1/courses | findstr "200" >nul && echo [OK] Laravel API (8000) san sang! || echo [WARN] Laravel API chua phan hoi!

echo.
echo ======================================================================
echo [OK] DA KICH HOAT XONG 4 DICH VU!
echo   1. Laravel API:    http://127.0.0.1:8000
echo   2. FastAPI AI:     http://127.0.0.1:8001/docs
echo   3. Queue Worker:   Chay nen xu ly ASR + Viterbi BGE-M3 + Gemini
echo   4. Ngrok / LAN:    Doi URL truc tiep bang bieu tuong Server tren App!
echo ======================================================================
pause
