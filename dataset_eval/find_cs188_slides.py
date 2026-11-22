import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

url = 'https://inst.eecs.berkeley.edu/~cs188/sp24/'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        content = resp.read().decode('utf-8')
    matches = re.findall(r'href="([^"]+lec01[^"]*|[^"]*lecture.?1[^"]*|[^"]*slides[^"]*)"', content, re.IGNORECASE)
    print("Found matches:")
    for m in set(matches):
        print("  ", m)
except Exception as e:
    print("Error:", e)
