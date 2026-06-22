// SPDX-License-Identifier: GPL-2.0-only

#define ZSTD_STATIC_LINKING_ONLY

#include "../../../crypto/abk_zstdp/zstdp_namespace.h"

#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>
#include <linux/zstd.h>

#include "backend_zstdp.h"

#define ZSTDP_DEF_LEVEL 3

struct zstdp_ctx {
	ZSTD_CCtx *cctx;
	ZSTD_DCtx *dctx;
	void *cwksp;
	void *dwksp;
};

static void zstdp_release_params(struct zcomp_params *params)
{
	params->drv_data = NULL;
}

static int zstdp_setup_params(struct zcomp_params *params)
{
	if (params->dict_sz)
		return -EOPNOTSUPP;

	if (params->level == ZCOMP_PARAM_NO_LEVEL)
		params->level = ZSTDP_DEF_LEVEL;

	return 0;
}

static void zstdp_destroy(struct zcomp_ctx *ctx)
{
	struct zstdp_ctx *zctx = ctx->context;

	if (!zctx)
		return;

	vfree(zctx->cwksp);
	vfree(zctx->dwksp);
	kfree(zctx);
	ctx->context = NULL;
}

static int zstdp_create(struct zcomp_params *params, struct zcomp_ctx *ctx)
{
	struct zstdp_ctx *zctx;
	size_t wksp_size;

	zctx = kzalloc(sizeof(*zctx), GFP_KERNEL);
	if (!zctx)
		return -ENOMEM;

	ctx->context = zctx;

	wksp_size = ZSTD_estimateCCtxSize(params->level);
	zctx->cwksp = vzalloc(wksp_size);
	if (!zctx->cwksp)
		goto error;

	zctx->cctx = ZSTD_initStaticCCtx(zctx->cwksp, wksp_size);
	if (!zctx->cctx)
		goto error;

	wksp_size = ZSTD_estimateDCtxSize();
	zctx->dwksp = vzalloc(wksp_size);
	if (!zctx->dwksp)
		goto error;

	zctx->dctx = ZSTD_initStaticDCtx(zctx->dwksp, wksp_size);
	if (!zctx->dctx)
		goto error;

	return 0;

error:
	zstdp_destroy(ctx);
	return -ENOMEM;
}

static int zstdp_compress(struct zcomp_params *params, struct zcomp_ctx *ctx,
			  struct zcomp_req *req)
{
	struct zstdp_ctx *zctx = ctx->context;
	size_t ret;

	ret = ZSTD_compressCCtx(zctx->cctx, req->dst, req->dst_len,
				req->src, req->src_len, params->level);
	if (ZSTD_isError(ret))
		return -EINVAL;

	req->dst_len = ret;
	return 0;
}

static int zstdp_decompress(struct zcomp_params *params, struct zcomp_ctx *ctx,
			    struct zcomp_req *req)
{
	struct zstdp_ctx *zctx = ctx->context;
	size_t ret;

	(void)params;

	ret = ZSTD_decompressDCtx(zctx->dctx, req->dst, req->dst_len,
				  req->src, req->src_len);
	if (ZSTD_isError(ret))
		return -EINVAL;

	req->dst_len = ret;
	return 0;
}

const struct zcomp_ops backend_zstdp = {
	.compress	= zstdp_compress,
	.decompress	= zstdp_decompress,
	.create_ctx	= zstdp_create,
	.destroy_ctx	= zstdp_destroy,
	.setup_params	= zstdp_setup_params,
	.release_params	= zstdp_release_params,
	.name		= "zstdp",
};
