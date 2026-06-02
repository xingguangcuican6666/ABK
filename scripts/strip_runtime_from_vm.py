from pathlib import Path

path = Path("app/src/main/java/com/abk/kernel/viewmodel/MainViewModel.kt")
lines = path.read_text(encoding="utf-8").splitlines()
# 1-based ranges to delete, descending
delete_ranges = [
    (4737, 4739),
    (4421, 4426),
    (4141, 4206),
    (3984, 3988),
    (3802, 3878),
    (643, 998),
]
for start, end in delete_ranges:
    del lines[start - 1 : end]
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("new line count", len(lines))
