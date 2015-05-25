# transforms project versions in project.clj's to $1. Primarily used
# for incremental builds.

old_version=$(awk '/defproject/ {print $3;}' project.clj)
new_version="\"$1\""

echo "Transforming from ${old_version} to ${new_version}"

for f in `find . -name project.clj`; do 
  sed -r "s/(defproject [^\"]*)${old_version}/\1${new_version}/" -i ${f}
done

# also transform top level project :version entry
sed -r "s/(org.immutant[ ]*)${old_version}/\1${new_version}/" -i project.clj
