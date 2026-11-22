import urllib.request, json, sys
sys.stdout.reconfigure(encoding='utf-8')

for lec_id in [1, 2, 3, 5]:
    req = urllib.request.urlopen(f'http://127.0.0.1:8000/api/v1/lectures/{lec_id}/workspace')
    data = json.loads(req.read().decode('utf-8'))
    title = data['lecture']['title']
    print(f'=== LECTURE {lec_id}: {title} ===')
    for s in data['slides']:
        links = s.get('cross_chapter_links', [])
        if links:
            s_no = s['slide_number']
            print(f'  Slide #{s_no}: {len(links)} links:')
            for l in links:
                print(f"    [{l['link_direction']}] {l['concept_name_vi']} -> {l['badge_text']}")
