package com.abk.kernel.ui.screens.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildPlan
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixBuildPlanLibraryScreen(vm:MainViewModel,onClose:()->Unit){
    val state by vm.uiState.collectAsState()
    val plans=state.buildPlans
    var renameTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var shareTarget by remember { mutableStateOf<BuildPlan?>(null) }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar={TopAppBar(title=stringResource(R.string.build_plan_library),largeTitle=stringResource(R.string.build_plan_library),scrollBehavior=scrollBehavior,navigationIcon={IconButton(onClick=onClose){Icon(MiuixIcons.Back,null)}})},
        containerColor=colorScheme.surface
    ){ it ->
        if(plans.isEmpty()){
            Box(Modifier.fillMaxSize().padding(it),contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(32.dp)){
                    Icon(Icons.Filled.FolderOpen,null,tint=colorScheme.onSurfaceVariantActions,modifier=Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.build_no_plans),fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.build_no_plans_desc),fontSize=14.sp,color=colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.build_no_plans_hint),fontSize=12.sp,color=colorScheme.onSurfaceVariantActions)
                }
            }
        }else LazyColumn(
            Modifier.fillMaxSize().padding(it).padding(horizontal=12.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding=PaddingValues(vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            items(plans,key={it.id}){plan->PlanCard(plan,vm,onClose,{renameTarget=plan;renameName=plan.name},{deleteTarget=plan},{shareTarget=plan})}
        }
    }

    if(renameTarget!=null){
        WindowDialog(title=stringResource(R.string.build_rename_plan),show=true,onDismissRequest={renameTarget=null}){
            TextField(value=renameName,onValueChange={renameName=it},label=stringResource(R.string.build_plan_name),modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                TextButton(text=stringResource(R.string.cancel),onClick={renameTarget=null},modifier=Modifier.weight(1f))
                Button(onClick={vm.renameBuildPlan(renameTarget!!.id,renameName);renameTarget=null},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text(stringResource(R.string.build_save))}
            }
        }
    }
    if(deleteTarget!=null){
        WindowDialog(title=stringResource(R.string.build_delete_plan),show=true,onDismissRequest={deleteTarget=null}){
            Text(stringResource(R.string.build_delete_plan_confirm,deleteTarget!!.name),fontSize=14.sp,color=colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                TextButton(text=stringResource(R.string.cancel),onClick={deleteTarget=null},modifier=Modifier.weight(1f))
                Button(onClick={vm.deleteBuildPlan(deleteTarget!!.id);deleteTarget=null},colors=ButtonDefaults.buttonColors(colorScheme.error),modifier=Modifier.weight(1f)){Text(stringResource(R.string.delete))}
            }
        }
    }
    if(shareTarget!=null){
        val plan = shareTarget!!
        val ctx = LocalContext.current
        val clipboardLabel = stringResource(R.string.build_plan_clipboard_label)
        val codeCopiedMsg = stringResource(R.string.build_plan_code_copied)
        WindowDialog(title=stringResource(R.string.build_share_plan),show=true,onDismissRequest={shareTarget=null}){
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(plan.name,fontSize=14.sp,fontWeight=FontWeight.Medium,color=colorScheme.onSurface)
                Text(buildPlanSummaryText(plan.config),fontSize=13.sp,color=colorScheme.onSurfaceVariantSummary)
                Text(stringResource(R.string.build_share_plan_desc),fontSize=12.sp,color=colorScheme.onSurfaceVariantActions)
            }
            Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                TextButton(text=stringResource(R.string.build_features_only),onClick={
                    val code = vm.shareBuildPlanCode(plan.config,plan.name,com.abk.kernel.viewmodel.BuildPlanShareScope.FEATURES_ONLY)
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(clipboardLabel,code))
                    Toast.makeText(ctx,codeCopiedMsg,Toast.LENGTH_SHORT).show()
                    shareTarget=null
                },modifier=Modifier.weight(1f))
                Button(onClick={
                    val code = vm.shareBuildPlanCode(plan.config,plan.name,com.abk.kernel.viewmodel.BuildPlanShareScope.FULL)
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(clipboardLabel,code))
                    Toast.makeText(ctx,codeCopiedMsg,Toast.LENGTH_SHORT).show()
                    shareTarget=null
                },colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text(stringResource(R.string.build_full_plan))}
            }
        }
    }
}

@Composable
private fun PlanCard(plan:BuildPlan,vm:MainViewModel,onClose:()->Unit,onRename:()->Unit,onDelete:()->Unit,onShare:()->Unit){
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Filled.FolderOpen,null,tint=colorScheme.onSurface,modifier=Modifier.size(20.dp))
                Column(Modifier.weight(1f)){
                    Text(plan.name,fontSize=16.sp,fontWeight=FontWeight.Medium,color=colorScheme.onSurface)
                    Text(buildPlanSummaryText(plan.config),fontSize=13.sp,color=colorScheme.onSurfaceVariantSummary,maxLines=3)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={vm.applyBuildPlan(plan);onClose()},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){
                    Icon(Icons.Filled.Edit,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.build_apply_edit))
                }
                Button(onClick=onShare,colors=ButtonDefaults.buttonColors(),modifier=Modifier.weight(1f)){
                    Icon(Icons.Filled.Share,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.build_share))
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onRename,colors=ButtonDefaults.buttonColors(),modifier=Modifier.weight(1f)){
                    Icon(Icons.Filled.Edit,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.build_rename))
                }
                Button(onClick=onDelete,colors=ButtonDefaults.buttonColors(colorScheme.error),modifier=Modifier.weight(1f)){
                    Icon(Icons.Filled.Delete,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

private fun buildPlanSummaryText(config: com.abk.kernel.data.model.KernelBuildConfig): String {
    // Simple summary: version, KSU, main features
    val parts=mutableListOf("${config.kernelVersion}.${config.subLevel}")
    parts+=config.kernelsuVariant
    if(!config.cancelSusfs)parts+="SUSFS"
    if(config.useZram)parts+="ZRAM"
    if(config.useKpm)parts+="KPM"
    if(config.useBbg)parts+="BBG"
    return parts.joinToString(" · ")
}
