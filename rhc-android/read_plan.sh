#!/bin/bash
cd /workspaces/RHC-App
rm -f output.txt

echo "===== READING HOMEVISITS PLAN =====" >> output.txt
if [ -d "homevisits-plan" ]; then
    # Find all text/markdown files and read them
    while IFS= read -r f; do
        echo "===== $f =====" >> output.txt
        cat "$f" >> output.txt
        echo -e "\n\n" >> output.txt
    done < <(find homevisits-plan -type f)
else
    echo "Directory homevisits-plan not found!" >> output.txt
    # Let's do a fallback search just in case the name is slightly different
    echo "Fallback search for '*plan*':" >> output.txt
    find . -type d -iname "*plan*" >> output.txt
fi

cat output.txt
