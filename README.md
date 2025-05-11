# Promocje dla metod płatności

## Opis projektu

Aplikacja optymalizuje sposób płatności za zamówienia w sklepie internetowym. Uwzględnia rabaty bankowe oraz punkty lojalnościowe, by maksymalizować łączny rabat i spełnić wymagania systemu.

## Jak to działa

Dla każdej transakcji algorytm:

- Wybiera najkorzystniejszą dostępną metodę płatności (punkty lub karta z rabatem),
- Stosuje odpowiednie promocje, zgodnie z zasadami (np. pełna płatność kartą lub min. 10% punktami),
- Dba o to, by nie przekroczyć limitów danej metody płatności.

## Wymagania

- Java 21
- System plików z dostępem do dwóch plików JSON:
  - `orders.json` – zamówienia,
  - `paymentmethods.json` – dostępne metody płatności.

## Struktura danych wejściowych

### `orders.json` (lista zamówień):

```json
[
  {
    "id": "ORDER1",
    "value": "150.00",
    "promotions": ["mZysk", "BosBankrut"]
  }
]
```

- `id`: identyfikator zamówienia,
- `value`: kwota zamówienia,
- `promotions`: opcjonalna lista dostępnych promocji.

### `paymentmethods.json` (lista metod płatności):

```json
[
  {
    "id": "mZysk",
    "discount": "10",
    "limit": "200.00"
  },
  {
    "id": "PUNKTY",
    "discount": "15",
    "limit": "100.00"
  }
]
```

- `id`: identyfikator metody płatności (np. karta lub punkty),
- `discount`: rabat procentowy,
- `limit`: dostępne środki.

## Uruchomienie

```bash
java -jar app.jar /ścieżka/orders.json /ścieżka/paymentmethods.json
```

## Wyjście

Na standardowym wyjściu aplikacja wypisze:

```
mZysk 165.00
BosBankrut 190.00
PUNKTY 100.00
```

## Budowanie

Projekt używa narzędzia Maven.

```bash
mvn clean package
```

Wygenerowany plik `.jar` będzie w folderze `target/` i będzie zawierał wszystkie zależności (`fat-jar`).

## Testowanie

Kod zawiera testy jednostkowe pokrywające kluczową logikę wyboru metody płatności. Uruchom je komendą:

```bash
mvn test
```

## Dodatki

- Obsługa błędów (np. brak środków, zły format danych)
- Wydajność zoptymalizowana dla do 10 000 zamówień
