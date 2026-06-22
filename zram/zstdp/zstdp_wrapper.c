// SPDX-License-Identifier: GPL-2.0-only
/*
 * zstdp wrapper built on top of a vendored, symbol-prefixed zstd tree.
 */

#define ZSTD_STATIC_LINKING_ONLY

#include "zstdp_namespace.h"

#include <crypto/internal/scompress.h>
#include <linux/crypto.h>
#include <linux/init.h>
#include <linux/interrupt.h>
#include <linux/mm.h>
#include <linux/module.h>
#include <linux/net.h>
#include <linux/version.h>
#include <linux/vmalloc.h>
#include <linux/zstd.h>

#define ZSTDP_DEF_LEVEL 3

struct zstdp_ctx {
	ZSTD_CCtx *cctx;
	ZSTD_DCtx *dctx;
	void *cwksp;
	void *dwksp;
};

static int zstdp_comp_init(struct zstdp_ctx *ctx)
{
	size_t wksp_size;

	wksp_size = ZSTD_estimateCCtxSize(ZSTDP_DEF_LEVEL);
	ctx->cwksp = vzalloc(wksp_size);
	if (!ctx->cwksp)
		return -ENOMEM;

	ctx->cctx = ZSTD_initStaticCCtx(ctx->cwksp, wksp_size);
	if (!ctx->cctx) {
		vfree(ctx->cwksp);
		ctx->cwksp = NULL;
		return -EINVAL;
	}

	return 0;
}

static int zstdp_decomp_init(struct zstdp_ctx *ctx)
{
	size_t wksp_size;

	wksp_size = ZSTD_estimateDCtxSize();
	ctx->dwksp = vzalloc(wksp_size);
	if (!ctx->dwksp)
		return -ENOMEM;

	ctx->dctx = ZSTD_initStaticDCtx(ctx->dwksp, wksp_size);
	if (!ctx->dctx) {
		vfree(ctx->dwksp);
		ctx->dwksp = NULL;
		return -EINVAL;
	}

	return 0;
}

static void zstdp_comp_exit(struct zstdp_ctx *ctx)
{
	vfree(ctx->cwksp);
	ctx->cwksp = NULL;
	ctx->cctx = NULL;
}

static void zstdp_decomp_exit(struct zstdp_ctx *ctx)
{
	vfree(ctx->dwksp);
	ctx->dwksp = NULL;
	ctx->dctx = NULL;
}

static int __zstdp_init(void *ctx)
{
	int ret;

	ret = zstdp_comp_init(ctx);
	if (ret)
		return ret;

	ret = zstdp_decomp_init(ctx);
	if (ret)
		zstdp_comp_exit(ctx);

	return ret;
}

static void __zstdp_exit(void *ctx)
{
	zstdp_comp_exit(ctx);
	zstdp_decomp_exit(ctx);
}

static void *__zstdp_alloc_ctx(void)
{
	struct zstdp_ctx *ctx;
	int ret;

	ctx = kzalloc(sizeof(*ctx), GFP_KERNEL);
	if (!ctx)
		return ERR_PTR(-ENOMEM);

	ret = __zstdp_init(ctx);
	if (ret) {
		kfree(ctx);
		return ERR_PTR(ret);
	}

	return ctx;
}

static void __zstdp_free_ctx(void *ctx)
{
	__zstdp_exit(ctx);
	kfree_sensitive(ctx);
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 12, 0)
static void *zstdp_alloc_ctx(void)
{
	return __zstdp_alloc_ctx();
}

static void zstdp_free_ctx(void *ctx)
{
	__zstdp_free_ctx(ctx);
}
#else
static void *zstdp_alloc_ctx(struct crypto_scomp *tfm)
{
	return __zstdp_alloc_ctx();
}

static void zstdp_free_ctx(struct crypto_scomp *tfm, void *ctx)
{
	__zstdp_free_ctx(ctx);
}
#endif

static int zstdp_init(struct crypto_tfm *tfm)
{
	struct zstdp_ctx *ctx = crypto_tfm_ctx(tfm);

	return __zstdp_init(ctx);
}

static void zstdp_exit(struct crypto_tfm *tfm)
{
	struct zstdp_ctx *ctx = crypto_tfm_ctx(tfm);

	__zstdp_exit(ctx);
}

static int __zstdp_compress(const u8 *src, unsigned int slen,
			    u8 *dst, unsigned int *dlen, void *ctx)
{
	size_t out_len;
	struct zstdp_ctx *zctx = ctx;

	out_len = ZSTD_compressCCtx(zctx->cctx, dst, *dlen, src, slen, ZSTDP_DEF_LEVEL);
	if (ZSTD_isError(out_len))
		return -EINVAL;

	*dlen = out_len;
	return 0;
}

static int zstdp_compress(struct crypto_tfm *tfm, const u8 *src,
			  unsigned int slen, u8 *dst, unsigned int *dlen)
{
	struct zstdp_ctx *ctx = crypto_tfm_ctx(tfm);

	return __zstdp_compress(src, slen, dst, dlen, ctx);
}

static int zstdp_scompress(struct crypto_scomp *tfm, const u8 *src,
			   unsigned int slen, u8 *dst, unsigned int *dlen,
			   void *ctx)
{
	return __zstdp_compress(src, slen, dst, dlen, ctx);
}

static int __zstdp_decompress(const u8 *src, unsigned int slen,
			      u8 *dst, unsigned int *dlen, void *ctx)
{
	size_t out_len;
	struct zstdp_ctx *zctx = ctx;

	out_len = ZSTD_decompressDCtx(zctx->dctx, dst, *dlen, src, slen);
	if (ZSTD_isError(out_len))
		return -EINVAL;

	*dlen = out_len;
	return 0;
}

static int zstdp_decompress(struct crypto_tfm *tfm, const u8 *src,
			    unsigned int slen, u8 *dst, unsigned int *dlen)
{
	struct zstdp_ctx *ctx = crypto_tfm_ctx(tfm);

	return __zstdp_decompress(src, slen, dst, dlen, ctx);
}

static int zstdp_sdecompress(struct crypto_scomp *tfm, const u8 *src,
			     unsigned int slen, u8 *dst, unsigned int *dlen,
			     void *ctx)
{
	return __zstdp_decompress(src, slen, dst, dlen, ctx);
}

static struct crypto_alg alg = {
	.cra_name		= "zstdp",
	.cra_driver_name	= "zstdp-generic",
	.cra_flags		= CRYPTO_ALG_TYPE_COMPRESS,
	.cra_ctxsize		= sizeof(struct zstdp_ctx),
	.cra_module		= THIS_MODULE,
	.cra_init		= zstdp_init,
	.cra_exit		= zstdp_exit,
	.cra_u			= {
		.compress = {
			.coa_compress	= zstdp_compress,
			.coa_decompress	= zstdp_decompress,
		},
	},
};

static struct scomp_alg scomp = {
	.alloc_ctx		= zstdp_alloc_ctx,
	.free_ctx		= zstdp_free_ctx,
	.compress		= zstdp_scompress,
	.decompress		= zstdp_sdecompress,
	.base			= {
			.cra_name	= "zstdp",
			.cra_driver_name = "zstdp-scomp",
			.cra_module	 = THIS_MODULE,
		},
};

static int __init zstdp_mod_init(void)
{
	int ret;

	ret = crypto_register_alg(&alg);
	if (ret)
		return ret;

	ret = crypto_register_scomp(&scomp);
	if (ret)
		crypto_unregister_alg(&alg);

	return ret;
}

static void __exit zstdp_mod_fini(void)
{
	crypto_unregister_alg(&alg);
	crypto_unregister_scomp(&scomp);
}

subsys_initcall(zstdp_mod_init);
module_exit(zstdp_mod_fini);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Zstdp Compression Algorithm");
MODULE_ALIAS("zstdp");
MODULE_ALIAS_CRYPTO("zstdp");
