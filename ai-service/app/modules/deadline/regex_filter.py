import re
import unicodedata

# Bộ từ khóa chỉ dấu hiệu deadline & hành động học tập trong tiếng Việt
DEADLINE_KEYWORDS = [
    r"nộp",
    r"hạn\s*(?:chót|nộp|nhận|trả|cuối)?",
    r"deadline",
    r"bài\s*tập",
    r"bài\s*thực\s*hành",
    r"lab\s*\d*",
    r"đồ\s*án",
    r"tiểu\s*luận",
    r"báo\s*cáo",
    r"trước\s*(?:\d+|ngày|tiết|giờ|hôm|mai)",
    r"dời\s*(?:hạn|lại)?",
    r"gia\s*hạn",
    r"hủy\s*bài",
    r"kiểm\s*tra",
    r"quiz",
    r"thi\s*giữa\s*kỳ",
    r"chuẩn\s*bị",
    r"đọc\s*trước",
    r"đến\s*hết\s*ngày",
    r"học\s*phí",
    r"họp\s*(?:nhóm|discord|meet)?",
    r"điểm\s*danh"
]

COMPILED_PATTERNS = [re.compile(kw, re.IGNORECASE) for kw in DEADLINE_KEYWORDS]

def is_deadline_candidate(text: str) -> bool:
    """
    Tiền lọc siêu nhanh (Cheap Pre-filter) bằng Regex:
    Trả về True nếu tin nhắn có ít nhất 1 dấu hiệu liên quan deadline/bài tập/nhắc nhở học tập.
    """
    if not text:
        return False
    
    text_norm = unicodedata.normalize('NFC', text).lower()
    
    for pattern in COMPILED_PATTERNS:
        if pattern.search(text_norm):
            return True
            
    return False
