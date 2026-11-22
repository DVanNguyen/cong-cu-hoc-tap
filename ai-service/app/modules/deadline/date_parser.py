import re
from datetime import datetime, timedelta
import zoneinfo

VN_TZ = zoneinfo.ZoneInfo("Asia/Ho_Chi_Minh")

def parse_vietnamese_relative_date(expression: str, base_time: datetime) -> datetime | None:
    """
    Quy đổi cụm từ thời gian tiếng Việt xác định bằng luật toán học.
    base_time: thời điểm nhận tin nhắn (Asia/Ho_Chi_Minh).
    """
    if not expression:
        return None
        
    expr = expression.strip().lower()
    base_date = base_time.date()
    
    # 1. Trích xuất giờ kết thúc / hạn chót nếu có (ví dụ: 23h59, 17:00, 18h, 9h tối, 12h đêm, từ 20h đến 22h)
    hour = 23
    minute = 59
    
    # Ưu tiên giờ kết thúc nếu có dạng "đến Xh"
    to_hour_match = re.search(r"đến\s*(\d{1,2})[h:](\d{2})?", expr)
    if to_hour_match:
        hour = int(to_hour_match.group(1))
        minute = int(to_hour_match.group(2)) if to_hour_match.group(2) else 0
    else:
        time_matches = list(re.finditer(r"\b(\d{1,2})[h:](\d{2})?\b", expr))
        if time_matches:
            # Lấy mốc giờ cuối cùng được nhắc tới
            last_tm = time_matches[-1]
            hour = int(last_tm.group(1))
            minute = int(last_tm.group(2)) if last_tm.group(2) else 0

    # Hiệu chỉnh buổi tối / đêm / trưa / sáng
    if "tối" in expr and hour < 12:
        hour += 12
    elif ("đêm" in expr or "hết ngày" in expr) and hour == 12:
        hour = 23
        minute = 59
    elif "trưa" in expr and hour == 12:
        hour = 12
        minute = 0

    # 2. Xử lý "dời lại X ngày", "hoãn X ngày", "thêm X ngày"
    offset_day_match = re.search(r"(?:dời|hoãn|thêm)\s*(?:lại)?\s*(\d+)\s*(?:ngày|h|tiếng)", expr)
    if offset_day_match:
        val = int(offset_day_match.group(1))
        if "h" in offset_day_match.group(0) or "tiếng" in offset_day_match.group(0):
            target_dt = base_time + timedelta(hours=val)
            return target_dt
        else:
            target_date = base_date + timedelta(days=val)
            return datetime(target_date.year, target_date.month, target_date.day, hour, minute, tzinfo=VN_TZ)

    # 3. Xử lý ngày cụ thể: DD/MM hoặc DD tháng MM (tránh bắt nhầm năm học ví dụ 2026-2027)
    for m in re.finditer(r"\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{4}))?\b", expr):
        d_val = int(m.group(1))
        m_val = int(m.group(2))
        y_val = int(m.group(3)) if m.group(3) else base_date.year
        if 1 <= m_val <= 12 and 1 <= d_val <= 31:
            try:
                # Nếu có giờ chỉ định trong cụm trước ngày (ví dụ trước 17h ngày 05/11)
                return datetime(y_val, m_val, d_val, hour, minute, tzinfo=VN_TZ)
            except ValueError:
                pass

    date_word_match = re.search(r"(\d{1,2})\s+tháng\s+(\d{1,2})(?:\s+năm\s+(\d{4}))?", expr)
    if date_word_match:
        d_val = int(date_word_match.group(1))
        m_val = int(date_word_match.group(2))
        y_val = int(date_word_match.group(3)) if date_word_match.group(3) else base_date.year
        try:
            return datetime(y_val, m_val, d_val, hour, minute, tzinfo=VN_TZ)
        except ValueError:
            pass

    # 4. Xử lý "hôm nay", "tối nay", "chiều nay", "sáng nay", "đêm nay"
    if any(k in expr for k in ["hôm nay", "tối nay", "chiều nay", "sáng nay", "đêm nay"]):
        target_date = base_date
        return datetime(target_date.year, target_date.month, target_date.day, hour, minute, tzinfo=VN_TZ)

    # 5. Xử lý "ngày mai", "sáng mai", "tối mai", "mai"
    if any(k in expr for k in ["ngày mai", "tối mai", "sáng mai"]) or re.search(r"\bmai\b", expr):
        target_date = base_date + timedelta(days=1)
        if "sáng mai" in expr and hour == 23:
            hour, minute = 7, 30
        return datetime(target_date.year, target_date.month, target_date.day, hour, minute, tzinfo=VN_TZ)

    # 6. Xử lý "ngày kia"
    if "ngày kia" in expr:
        target_date = base_date + timedelta(days=2)
        return datetime(target_date.year, target_date.month, target_date.day, hour, minute, tzinfo=VN_TZ)

    # 7. Xử lý thứ trong tuần: "thứ 6 tuần sau", "Chủ nhật ngày 25/10", "thứ 6 này"
    day_mapping = {
        "thứ 2": 0, "thứ hai": 0,
        "thứ 3": 1, "thứ ba": 1,
        "thứ 4": 2, "thứ tư": 2,
        "thứ 5": 3, "thứ năm": 3,
        "thứ 6": 4, "thứ sáu": 4,
        "thứ 7": 5, "thứ bảy": 5,
        "chủ nhật": 6, "cn": 6
    }
    
    for kw, target_weekday in day_mapping.items():
        if kw in expr:
            current_weekday = base_time.weekday()
            days_ahead = target_weekday - current_weekday
            if "tuần sau" in expr:
                days_ahead += 7
            elif "này" in expr:
                if days_ahead < 0:
                    days_ahead += 7
            elif days_ahead <= 0:
                days_ahead += 7
            target_date = base_date + timedelta(days=days_ahead)
            return datetime(target_date.year, target_date.month, target_date.day, hour, minute, tzinfo=VN_TZ)

    # 8. Xử lý "tuần sau" chung
    if "tuần sau" in expr:
        target_date = base_date + timedelta(days=7)
        if "kiểm tra" in expr or "thi" in expr:
            hour, minute = 8, 0
        return datetime(target_date.year, target_date.month, target_date.day, hour, minute, tzinfo=VN_TZ)

    # 9. Xử lý "buổi sau", "tiết sau", "buổi tới"
    if any(k in expr for k in ["buổi sau", "tiết sau", "buổi tới"]):
        target_date = base_date + timedelta(days=2 if "buổi tới" in expr else 3)
        return datetime(target_date.year, target_date.month, target_date.day, 7, 30, tzinfo=VN_TZ)

    # 10. Fallback cho tuần học (ví dụ "tuần 12")
    week_match = re.search(r"tuần\s*(\d{1,2})", expr)
    if week_match:
        target_date = base_date + timedelta(days=30)
        return datetime(target_date.year, target_date.month, target_date.day, 23, 59, tzinfo=VN_TZ)

    return None
