#!/bin/bash
cd /workspaces/RHC-App || exit
rm -f output.txt

echo "===== READING HOMEVISITS PLAN =====" >> output.txt
if [ -d "HOMEVISITS_PLAN" ]; then
    # Find all text/markdown files and read them
    while IFS= read -r f; do
        echo "===== $f =====" >> output.txt
        cat "$f" >> output.txt
        echo -e "\n\n" >> output.txt
    done < <(find HOMEVISITS_PLAN -type f)
else
    echo "Directory HOMEVISITS_PLAN not found!" >> output.txt
    # Let's do a fallback search just in case the name is slightly different
    echo "Fallback search for '*plan*':" >> output.txt
    find . -type d -iname "*plan*" >> output.txt
fi

cat output.txt
