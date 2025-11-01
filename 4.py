import requests
import time
import json
import os
from urllib.parse import quote
from bs4 import BeautifulSoup

BASE_API = "https://harrypotter.fandom.com/ru/api.php"
BASE_URL = "https://harrypotter.fandom.com/ru/wiki/"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
}
INTERVAL = 5
START_CATEGORY = "Категория:Обзор"


def get_members(cat_name):
    members = []
    cont = ""
    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": cat_name,
            "cmlimit": "max",
            "format": "json",
        }
        if cont:
            params["cmcontinue"] = cont
        try:
            r = requests.get(BASE_API, params=params, timeout=20)
            r.raise_for_status()
            data = r.json()
        except Exception as e:
            print(e)
            time.sleep(5)
            continue
        members.extend(data["query"]["categorymembers"])
        if "continue" in data:
            cont = data["continue"]["cmcontinue"]
            time.sleep(0.3)
        else:
            break
    return members


def save(urls, cats):
    with open("urls.json", "w", encoding="utf-8") as f:
        json.dump(list(urls), f, ensure_ascii=False, indent=2)
    with open("cats.json", "w", encoding="utf-8") as f:
        json.dump(list(cats), f, ensure_ascii=False, indent=2)


def load():
    urls = set()
    cats = set()
    if os.path.exists("urls.json"):
        with open("urls.json", "r", encoding="utf-8") as f:
            urls = set(json.load(f))
    if os.path.exists("cats.json"):
        with open("cats.json", "r", encoding="utf-8") as f:
            cats = set(json.load(f))
    return urls, cats


def recursive(cat_name, urls, cats, counter=[0]):
    if cat_name in cats:
        return
    cats.add(cat_name)
    counter[0] += 1

    try:
        members = get_members(cat_name)
    except Exception as e:
        print(e)
        return

    for m in members:
        title = m["title"]
        ns = m["ns"]
        if ns == 0:
            urls.add(f"{BASE_URL}{quote(title)}")
        elif ns == 14:
            recursive(title, urls, cats, counter)

    if counter[0] % INTERVAL == 0:
        save(urls, cats)


def parse(url):
    try:
        r = requests.get(url, headers=HEADERS, timeout=20)
        r.raise_for_status()
    except Exception as e:
        print(e)
        return None

    soup = BeautifulSoup(r.text, "html.parser")
    data = {"url": url}

    name_tag = soup.select_one("h1.page-header__title")
    data["name"] = name_tag.text.strip() if name_tag else "N/A"

    infobox = soup.select_one("aside.portable-infobox")
    info_dict = {}
    if infobox:
        for item in infobox.select(".pi-item"):
            label = item.select_one(".pi-data-label")
            value = item.select_one(".pi-data-value")
            if label and value:
                key = label.text.strip()
                val = value.text.strip().replace("\n", ", ")
                info_dict[key] = val
    data["infobox"] = info_dict if info_dict else "N/A"

    summary_tag = soup.select_one(".mw-parser-output > p")
    data["summary"] = summary_tag.text.strip() if summary_tag else "N/A"

    full_content = soup.select_one("div.mw-parser-output")
    if full_content:
        for tag in full_content.find_all(["aside", "table", "sup", "div.toc", "script", "style"]):
            tag.decompose()
        data["full_text"] = full_content.text.strip()
    else:
        data["full_text"] = "N/A"

    data["categories"] = [a.text.strip() for a in soup.select(".page-footer__categories a")]

    return data


def scrape(urls, filename="all_pages.json"):
    all_data = []
    total = len(urls)

    for i, url in enumerate(urls):
        print(f"[{i+1}/{total}] {url}")
        result = parse(url)
        if result:
            all_data.append(result)
        time.sleep(0.5)

        if (i + 1) % 100 == 0:
            with open(filename, "w", encoding="utf-8") as f:
                json.dump(all_data, f, ensure_ascii=False, indent=2)

    with open(filename, "w", encoding="utf-8") as f:
        json.dump(all_data, f, ensure_ascii=False, indent=2)

    print(f"Собрано {len(all_data)} страниц")


def main():
    urls, cats = load()

    if not urls:
        recursive(START_CATEGORY, urls, cats)
        save(urls, cats)

    with open("all_pages.txt", "w", encoding="utf-8") as f:
        for url in sorted(urls):
            f.write(url + "\n")
    print(f"Сохранено {len(urls)} ссылок")

    scrape(sorted(urls))


if __name__ == "__main__":
    main()