-- =====================================================================
-- V5: reference data (regions, categories, starter product catalogue)
-- Names are in English, Amharic and Afaan Oromoo. Admins can extend the
-- catalogue through /api/v1/admin/categories and /api/v1/admin/products.
-- =====================================================================

insert into regions (id, code, name_en, name_am, name_om) values
    (gen_random_uuid(), 'AA',  'Addis Ababa',          'አዲስ አበባ',          'Finfinnee'),
    (gen_random_uuid(), 'OR',  'Oromia',               'ኦሮሚያ',            'Oromiyaa'),
    (gen_random_uuid(), 'AM',  'Amhara',               'አማራ',              'Amaaraa'),
    (gen_random_uuid(), 'TG',  'Tigray',               'ትግራይ',             'Tigraay'),
    (gen_random_uuid(), 'SD',  'Sidama',               'ሲዳማ',              'Sidaamaa'),
    (gen_random_uuid(), 'CE',  'Central Ethiopia',     'ማዕከላዊ ኢትዮጵያ',     'Itoophiyaa Giddugaleessaa'),
    (gen_random_uuid(), 'SE',  'South Ethiopia',       'ደቡብ ኢትዮጵያ',       'Itoophiyaa Kibbaa'),
    (gen_random_uuid(), 'SW',  'South West Ethiopia',  'ደቡብ ምዕራብ ኢትዮጵያ',  'Itoophiyaa Kibba Lixaa'),
    (gen_random_uuid(), 'AF',  'Afar',                 'አፋር',              'Affaar'),
    (gen_random_uuid(), 'SO',  'Somali',               'ሶማሊ',              'Somaalee'),
    (gen_random_uuid(), 'BG',  'Benishangul-Gumuz',    'ቤኒሻንጉል ጉሙዝ',      'Benishaangul-Gumuz'),
    (gen_random_uuid(), 'GM',  'Gambela',              'ጋምቤላ',             'Gambeellaa'),
    (gen_random_uuid(), 'HR',  'Harari',               'ሐረሪ',              'Hararii'),
    (gen_random_uuid(), 'DD',  'Dire Dawa',            'ድሬዳዋ',             'Dirre Dawaa');

insert into product_categories (id, slug, name_en, name_am, name_om, icon, sort_order) values
    (gen_random_uuid(), 'vegetables',     'Vegetables',        'አትክልት',        'Kuduraa',        'leaf',   1),
    (gen_random_uuid(), 'fruits',         'Fruits',            'ፍራፍሬ',         'Fuduraa',        'apple',  2),
    (gen_random_uuid(), 'cereals-grains', 'Cereals & Grains',  'እህል',           'Midhaan',        'wheat',  3),
    (gen_random_uuid(), 'pulses',         'Pulses',            'ጥራጥሬ',         'Boqqolloo',      'bean',   4),
    (gen_random_uuid(), 'dairy-eggs',     'Dairy & Eggs',      'ወተትና እንቁላል',   'Aannanii fi Hanqaaquu', 'egg', 5),
    (gen_random_uuid(), 'meat-poultry',   'Meat & Poultry',    'ስጋና ዶሮ',        'Foonii fi Lukkuu', 'drumstick', 6),
    (gen_random_uuid(), 'spices-herbs',   'Spices & Herbs',    'ቅመማ ቅመም',      'Foddaa',         'pepper', 7),
    (gen_random_uuid(), 'coffee-oilseeds','Coffee & Oilseeds', 'ቡናና ቅባት እህሎች',    'Buna fi Sanyii zayitaa', 'coffee', 8);

insert into products (id, category_id, slug, name_en, name_am, name_om, default_unit)
select gen_random_uuid(), c.id, p.slug, p.name_en, p.name_am, p.name_om, p.unit
from (values
    ('vegetables',      'tomato',        'Tomato',        'ቲማቲም',    'Timaatima',  'KG'),
    ('vegetables',      'onion',         'Onion',         'ሽንኩርት',   'Shunkurtii', 'KG'),
    ('vegetables',      'potato',        'Potato',        'ድንች',      'Dinnicha',   'KG'),
    ('vegetables',      'cabbage',       'Cabbage',       'ጎመን',      'Raafuu',     'KG'),
    ('vegetables',      'carrot',        'Carrot',        'ካሮት',      'Kaarotii',   'KG'),
    ('vegetables',      'green-pepper',  'Green Pepper',  'ቃሪያ',      'Qaariyaa',   'KG'),
    ('fruits',          'banana',        'Banana',        'ሙዝ',       'Muuzii',     'KG'),
    ('fruits',          'avocado',       'Avocado',       'አቮካዶ',     'Avokaadoo',  'KG'),
    ('fruits',          'mango',         'Mango',         'ማንጎ',      'Mangoo',     'KG'),
    ('fruits',          'orange',        'Orange',        'ብርቱካን',    'Burtukaana', 'KG'),
    ('cereals-grains',  'teff',          'Teff',          'ጤፍ',       'Xaafii',     'QUINTAL'),
    ('cereals-grains',  'wheat',         'Wheat',         'ስንዴ',      'Qamadii',    'QUINTAL'),
    ('cereals-grains',  'maize',         'Maize',         'በቆሎ',      'Boqqolloo',  'QUINTAL'),
    ('cereals-grains',  'barley',        'Barley',        'ገብስ',      'Garbuu',     'QUINTAL'),
    ('pulses',          'faba-bean',     'Faba Bean',     'ባቄላ',      'Baaqelaa',   'QUINTAL'),
    ('pulses',          'chickpea',      'Chickpea',      'ሽምብራ',     'Shumburaa',  'QUINTAL'),
    ('pulses',          'lentil',        'Lentil',        'ምስር',      'Missira',    'QUINTAL'),
    ('dairy-eggs',      'eggs',          'Eggs',          'እንቁላል',    'Hanqaaquu',  'TRAY'),
    ('dairy-eggs',      'milk',          'Milk',          'ወተት',      'Aannan',     'LITER'),
    ('meat-poultry',    'chicken',       'Chicken',       'ዶሮ',       'Lukkuu',     'KG'),
    ('spices-herbs',    'berbere-pepper','Red Pepper',    'ቀይ በርበሬ',   'Barbaree',   'KG'),
    ('coffee-oilseeds', 'coffee',        'Coffee (green)','ቡና',       'Buna',       'KG'),
    ('coffee-oilseeds', 'sesame',        'Sesame',        'ሰሊጥ',      'Saliixii',   'QUINTAL')
) as p(cat_slug, slug, name_en, name_am, name_om, unit)
join product_categories c on c.slug = p.cat_slug;
