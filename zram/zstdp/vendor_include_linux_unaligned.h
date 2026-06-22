/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __LINUX_UNALIGNED_H
#define __LINUX_UNALIGNED_H

/*
 * Newer zstd sources include <linux/unaligned.h>, but older Android kernel
 * trees only provide the asm-generic helpers. Prefer the native header when
 * present and fall back to the older implementation otherwise.
 */
#if __has_include_next(<linux/unaligned.h>)
#include_next <linux/unaligned.h>
#else
#include <asm-generic/unaligned.h>

#ifndef get_unaligned_le24
static inline u32 get_unaligned_le24(const void *p)
{
	const u8 *b = p;

	return (u32)b[0] | ((u32)b[1] << 8) | ((u32)b[2] << 16);
}
#endif

#ifndef get_unaligned_be24
static inline u32 get_unaligned_be24(const void *p)
{
	const u8 *b = p;

	return ((u32)b[0] << 16) | ((u32)b[1] << 8) | (u32)b[2];
}
#endif

#ifndef put_unaligned_le24
static inline void put_unaligned_le24(u32 val, void *p)
{
	u8 *b = p;

	b[0] = (u8)val;
	b[1] = (u8)(val >> 8);
	b[2] = (u8)(val >> 16);
}
#endif

#ifndef put_unaligned_be24
static inline void put_unaligned_be24(u32 val, void *p)
{
	u8 *b = p;

	b[0] = (u8)(val >> 16);
	b[1] = (u8)(val >> 8);
	b[2] = (u8)val;
}
#endif

#ifndef get_unaligned_be48
static inline u64 get_unaligned_be48(const void *p)
{
	const u8 *b = p;

	return ((u64)b[0] << 40) | ((u64)b[1] << 32) |
	       ((u64)b[2] << 24) | ((u64)b[3] << 16) |
	       ((u64)b[4] << 8) | (u64)b[5];
}
#endif

#ifndef put_unaligned_be48
static inline void put_unaligned_be48(u64 val, void *p)
{
	u8 *b = p;

	b[0] = (u8)(val >> 40);
	b[1] = (u8)(val >> 32);
	b[2] = (u8)(val >> 24);
	b[3] = (u8)(val >> 16);
	b[4] = (u8)(val >> 8);
	b[5] = (u8)val;
}
#endif
#endif

#endif /* __LINUX_UNALIGNED_H */
