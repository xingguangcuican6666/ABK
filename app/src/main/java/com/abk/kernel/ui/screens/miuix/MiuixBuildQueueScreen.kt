package com.abk.kernel.ui.screens.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildQueueItem
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun MiuixBuildQueueScreen(vm:MainViewModel,onClose:()->Unit){
    val state by vm.uiState.collectAsState()
    val queue=state.buildQueue
    val cancellingRunIds=state.cancellingWorkflowRunIds
    val terminalItems=queue.filter{it.status.isTerminalQueueStatus()}
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar={TopAppBar(title=stringResource(R.string.build_queue_status),largeTitle=stringResource(R.string.build_queue_status),scrollBehavior=scrollBehavior,navigationIcon={IconButton(onClick=onClose){Icon(MiuixIcons.Back,null)}})},
        containerColor=colorScheme.surface
    ){ it ->
        LazyColumn(
            Modifier.fillMaxSize().padding(it).padding(horizontal=12.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding=PaddingValues(vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            // Header card
            item{
                Card(Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Icon(Icons.Filled.Queue,null,tint=colorScheme.onSurface,modifier=Modifier.size(20.dp))
                        Column(Modifier.weight(1f)){
                            Text(stringResource(R.string.build_queue_status),fontSize=16.sp,fontWeight=FontWeight.Medium,color=colorScheme.onSurface)
                            Text(
                                if(queue.isEmpty())stringResource(R.string.build_queue_status_desc)
                                else stringResource(R.string.build_queue_status_count,queue.size,queue.count{it.status==BuildQueueItemStatus.PENDING}),
                                fontSize=13.sp,color=colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    if(terminalItems.isNotEmpty()){
                        HorizontalDivider()
                        Row(Modifier.padding(14.dp)){
                            Button(onClick={vm.clearCompletedBuildQueueItems()},colors=ButtonDefaults.buttonColors(colorScheme.error),modifier=Modifier.fillMaxWidth()){
                                Icon(Icons.Filled.Delete,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.build_clear_finished))
                            }
                        }
                    }else{
                        HorizontalDivider()
                        Text(
                            if(queue.isEmpty())stringResource(R.string.build_queue_empty)
                            else stringResource(R.string.build_dispatching_in_order),
                            fontSize=12.sp,color=colorScheme.onSurfaceVariantActions,
                            modifier=Modifier.padding(horizontal=14.dp,vertical=10.dp)
                        )
                    }
                }
            }

            if(queue.isEmpty()){
                // Empty state
                item{
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                Icon(Icons.Filled.Inbox,null,tint=colorScheme.onSurface,modifier=Modifier.size(20.dp))
                                Column(Modifier.weight(1f)){
                                    Text(stringResource(R.string.build_no_queue_items),fontSize=16.sp,fontWeight=FontWeight.Medium,color=colorScheme.onSurface)
                                    Text(stringResource(R.string.build_no_queue_items_desc),fontSize=13.sp,color=colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            Text(stringResource(R.string.build_queue_hint),fontSize=12.sp,color=colorScheme.onSurfaceVariantActions)
                        }
                    }
                }
            }else{
                items(queue,key={it.id}){item->QueueCard(item, cancellingRunIds, vm, onClose)}
            }
        }
    }
}

@Composable
private fun QueueCard(item:BuildQueueItem,cancellingRunIds:Set<Long>,vm:MainViewModel,onApply:()->Unit){
    val cancelling=item.runId>0L&&item.runId in cancellingRunIds
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(queueStatusIcon(item.status),null,tint=colorScheme.onSurface,modifier=Modifier.size(20.dp))
                Column(Modifier.weight(1f)){
                    Text(item.name.ifBlank{stringResource(R.string.build_queue_item)},fontSize=16.sp,fontWeight=FontWeight.Medium,color=colorScheme.onSurface)
                    Text(queuePlanSummary(item.config),fontSize=13.sp,color=colorScheme.onSurfaceVariantSummary,maxLines=2)
                }
            }
            // Status chips
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                QueueChip(queueStatusLabel(item.status),queueStatusColor(item.status))
                if(item.runNumber>0){QueueChip("#${item.runNumber}",colorScheme.primary.copy(alpha=0.72f))}
                if(item.runId>0L){QueueChip(stringResource(R.string.build_status_run_id,item.runId),colorScheme.onSurfaceVariantActions)}
            }
            // Error
            item.error?.let{
                Text(it,fontSize=12.sp,color= colorScheme.error)
            }
            // Actions
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={vm.updateBuildConfig(item.config);onApply()},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){
                    Icon(Icons.Filled.Edit,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.build_apply))
                }
                when(item.status){
                    BuildQueueItemStatus.PENDING->Button(onClick={vm.removeBuildQueueItem(item.id)},colors=ButtonDefaults.buttonColors(colorScheme.error),modifier=Modifier.weight(1f)){
                        Icon(Icons.Filled.Delete,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.build_remove))
                    }
                    BuildQueueItemStatus.DISPATCHING,BuildQueueItemStatus.RUNNING->Button(onClick={if(item.runId>0L)vm.cancelWorkflowRun(item.runId)},enabled=item.runId>0L&&!cancelling,colors=ButtonDefaults.buttonColors(colorScheme.error),modifier=Modifier.weight(1f)){
                        if(cancelling){CircularProgressIndicator(size=17.dp,strokeWidth=2.dp)}else{Icon(Icons.Filled.Cancel,null,modifier=Modifier.size(17.dp))}
                        Spacer(Modifier.width(6.dp));Text(if(cancelling)stringResource(R.string.status_cancelling)else stringResource(R.string.status_cancel))
                    }
                    BuildQueueItemStatus.FAILED->Button(onClick={vm.retryBuildQueueItem(item.id)},colors=ButtonDefaults.buttonColors(colorScheme.primary),modifier=Modifier.weight(1f)){
                        Icon(Icons.Filled.Refresh,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.retry))
                    }
                    else->{} // DONE, CANCELLED — no second action
                }
            }
        }
    }
}

@Composable
private fun QueueChip(label:String,color:Color){
    Row(
        Modifier.background(color.copy(alpha=0.14f),RoundedCornerShape(percent=50))
            .padding(horizontal=10.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Text(label,fontSize=12.sp,color=color,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

private fun queueStatusIcon(status: BuildQueueItemStatus): ImageVector = when(status){
    BuildQueueItemStatus.PENDING->Icons.Filled.Schedule
    BuildQueueItemStatus.DISPATCHING->Icons.Filled.CloudUpload
    BuildQueueItemStatus.RUNNING->Icons.Filled.RunCircle
    BuildQueueItemStatus.DONE->Icons.Filled.CheckCircle
    BuildQueueItemStatus.FAILED->Icons.Filled.Error
    BuildQueueItemStatus.CANCELLED->Icons.Filled.Cancel
}

@Composable
private fun queueStatusLabel(status: BuildQueueItemStatus): String = when(status){
    BuildQueueItemStatus.PENDING->stringResource(R.string.build_queue_pending)
    BuildQueueItemStatus.DISPATCHING->stringResource(R.string.build_queue_dispatching)
    BuildQueueItemStatus.RUNNING->stringResource(R.string.status_in_progress)
    BuildQueueItemStatus.DONE->stringResource(R.string.build_queue_done)
    BuildQueueItemStatus.FAILED->stringResource(R.string.status_failure)
    BuildQueueItemStatus.CANCELLED->stringResource(R.string.status_cancelled_label)
}

@Composable
private fun queueStatusColor(status: BuildQueueItemStatus): Color = when(status){
    BuildQueueItemStatus.PENDING->colorScheme.primary.copy(alpha=0.7f)
    BuildQueueItemStatus.DISPATCHING,BuildQueueItemStatus.RUNNING->colorScheme.primary
    BuildQueueItemStatus.DONE->colorScheme.primary
    BuildQueueItemStatus.FAILED-> colorScheme.error
    BuildQueueItemStatus.CANCELLED->colorScheme.onSurfaceVariantActions
}

private fun BuildQueueItemStatus.isTerminalQueueStatus(): Boolean =
    this in setOf(BuildQueueItemStatus.DONE,BuildQueueItemStatus.FAILED,BuildQueueItemStatus.CANCELLED)

private fun queuePlanSummary(config: com.abk.kernel.data.model.KernelBuildConfig): String {
    val parts=mutableListOf("${config.kernelVersion}.${config.subLevel}")
    parts+=config.kernelsuVariant
    if(!config.cancelSusfs)parts+="SUSFS"
    if(config.useZram)parts+="ZRAM"
    if(config.useKpm)parts+="KPM"
    return parts.joinToString(" · ")
}
