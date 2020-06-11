var md = angular.module('module_cluster/container', ['base']);
md.controller('MainCtrl', function ($scope, $http, uiTips, uiValid, uiLog) {
    $scope.ctrl = {};
    $scope.tmp = { targetIndex: 0 };

    var LocalStore = window.LocalStore;
    var store = new LocalStore(true);
    var old = store.get('cluster_container_old');

    var Page = window.Page;
    var params = Page.params();

    if (params.appId) {
        store.set('cluster_container_old', params)
    } else if (old) {
        params = old;
    }
    $scope.params = params;

    if (params.targetIndex) {
        $scope.tmp.targetIndex = params.targetIndex;
    }

    $scope.queryLl = function () {
        $http.get('/dms/container/list', { params: { appId: params.appId } }).success(function (data) {
            $scope.ll = _.sortBy(data, function (it) {
                return it.Instance_Index;
            });
        });
    };

    $scope.queryEventLl = function (pageNum) {
        var p = {
            pageNum: pageNum || 1,
            type: 'app',
            reason: $scope.tmp.reason,
            appId: params.appId
        };
        $http.get('/dms/event/list', { params: p }).success(function (data) {
            $scope.eventLl = data.list;
            $scope.eventPager = { pageNum: data.pageNum, pageSize: data.pageSize, totalCount: data.totalCount };
        });
    };

    var tabIndexChoosed = 0;
    $scope.changeTab = function (index, triggerIndex) {
        tabIndexChoosed = index;
        if (index == 0) {
            $scope.queryLl();
            $scope.tmp.refreshTime = new Date();
        } else if (index == 1) {
            $http.get('/dms/event/reason/list', { params: { type: 'app' } }).success(function (data) {
                $scope.tmp.reasonList = data;
            });
        } else if (index == 2) {
            $http.get('/dms/app/job/list', { params: { appId: params.appId } }).success(function (data) {
                $scope.jobList = data;
                $scope.tmp.refreshTime = new Date();
            });
        }

        var Page = window.Page;
        var isReg = Page.registerIntervalFunc(document.location.hash, 'refreshList');
        if (isReg) {
            uiLog.i('begin refreshList interval');
        }
        return true;
    };

    $scope.back = function () {
        Page.go('/page/cluster_app', {
            appId: $scope.appId,
            clusterId: params.clusterId,
            namespaceId: params.namespaceId
        });
    };

    $scope.inspect = function (x) {
        $http.get('/dms/container/manage/inspect', { params: { id: x.Id } }).success(function (data) {
            $.dialog({ title: 'Container Detail', content: '<pre style="height: 400px;">' + JSON.stringify(data.container, null, 2) + '</pre>' });
        });
    };

    $scope.opt = function (x, cmd) {
        uiTips.loading();
        $http.get('/dms/container/manage/' + cmd, { params: { id: x.Id } }).success(function (data) {
            uiTips.tips(JSON.stringify(data));
            $scope.queryLl();
        });
    };

    $scope.getJobTypeLabel = function (jobType) {
        var map = { 1: 'create', 2: 'remove', 3: 'scroll' };
        return map[jobType];
    };

    $scope.getJobStatusLabel = function (status) {
        var map = { '0': 'created', '1': 'processing', '-1': 'failed', '10': 'done' };
        return map[status];
    };

    $scope.showMessage = function (one) {
        $.dialog({ title: 'Job Message', content: '<pre style="height: 400px;">' + one.message + '</pre>' });
    };

    $scope.showBindList = function (x) {
        $http.get('/dms/container/manage/bind/list', { params: { id: x.Id } }).success(function (data) {
            $scope.tmp.containerId = x.Id;
            $scope.tmp.bindList = data;
            $scope.ctrl.isShowBinds = true;
        });
    };

    $scope.showPortBind = function (x) {
        $http.get('/dms/container/manage/port/bind', { params: { id: x.Id } }).success(function (data) {
            $.dialog({
                title: 'Port Binds',
                content: '<pre style="height: 400px;">' + JSON.stringify(data, null, 2) + '</pre>'
            });
        });
    };

    $scope.showBindContent = function (bindFileIndex) {
        var p = { id: $scope.tmp.containerId, bindFileIndex: bindFileIndex };
        $http.get('/dms/container/manage/bind/content', { params: p }).success(function (data) {
            $.dialog({ title: 'Bind Detail', content: '<pre style="height: 400px;">' + data + '</pre>' });
        });
    };

    var Page = window.Page;
    $scope.showJobLogList = function (one) {
        if (one) {
            $scope.tmp.job = one;
        }
        $http.get('/dms/app/job/log/list', { params: { jobId: $scope.tmp.job.id } }).success(function (data) {
            $scope.tmp.jobLogList = _.map(data, function (x) {
                // var messageFormat = JSON.stringify(JSON.parse(x.message), null, 2);
                var messageList = JSON.parse(x.message);
                _.each(messageList, function (x) {
                    if (x.message) {
                        x.isOk = x.message.endsWith(' - ok');
                    }
                });
                return { messageList: messageList, instanceIndex: x.instanceIndex, createdDate: x.createdDate };
            });
            $scope.ctrl.isShowJobLog = true;
            $scope.tmp.refreshTime = new Date();
            Page.fixCenter(lhgdialog.list['dialogJobLog_1']);
        });
    };

    $scope.refreshList = function () {
        if ($scope.ctrl.isShowJobLog) {
            $scope.showJobLogList();
        } else if ($scope.ctrl.isShowContainerStats) {
            $scope.changeContainerStatsTab(statsTabIndexChoosed, -1, true);
        }

        if (tabIndexChoosed == 2) {
            $http.get('/dms/app/job/list', { params: { appId: params.appId } }).success(function (data) {
                $scope.jobList = data;
            });
            $scope.tmp.refreshTime = new Date();
        } else if (tabIndexChoosed == 0) {
            $scope.queryLl();
            $scope.tmp.refreshTime = new Date();
        }
    };

    $scope.showStats = function (one) {
        $http.get('/dms/node/metric/queue', {
            params: {
                nodeIp: one.Node_Ip,
                containerId: one.Id,
                queueType: 'container',
                type: 'cpu'
            }
        }).success(function (data) {
            $scope.tmp.targetContainer = one;
            $scope.tmp.containerCpuChartData = { data: data.list, xData: data.timelineList };
            $scope.ctrl.isShowContainerStats = true;
            Page.fixCenter(lhgdialog.list['dialogContainerStats_1']);
        });
    };

    var statsTabIndexChoosed;
    $scope.changeContainerStatsTab = function (index, triggerIndex, isRefresh) {
        statsTabIndexChoosed = index;
        if (index == 0) {
            $http.get('/dms/node/metric/queue', {
                params: {
                    nodeIp: $scope.tmp.targetContainer.Node_Ip,
                    containerId: $scope.tmp.targetContainer.Id,
                    queueType: 'container',
                    type: 'cpu'
                }
            }).success(function (data) {
                $scope.tmp.containerCpuChartData = { data: data.list, xData: data.timelineList };
                Page.fixCenter(lhgdialog.list['dialogContainerStats_1']);
            });
        } else if (index == 1) {
            $http.get('/dms/node/metric/queue', {
                params: {
                    nodeIp: $scope.tmp.targetContainer.Node_Ip,
                    containerId: $scope.tmp.targetContainer.Id,
                    queueType: 'container',
                    type: 'mem'
                }
            }).success(function (data) {
                $scope.tmp.containerMemChartData = { data: data.list, xData: data.timelineList };
                Page.fixCenter(lhgdialog.list['dialogContainerStats_1']);
            });
        } else if (index == 2) {
            if (!isRefresh) {
                $http.get('/dms/node/metric/gauge/name/list', {
                    params: {
                        nodeIp: $scope.tmp.targetContainer.Node_Ip,
                        containerId: $scope.tmp.targetContainer.Id
                    }
                }).success(function (data) {
                    $scope.tmp.gaugeName = '';
                    $scope.tmp.gaugeNameList = data;
                    $scope.tmp.containerGaugeChartData = { data: [], xData: [] };
                    Page.fixCenter(lhgdialog.list['dialogContainerStats_1']);
                });
            } else {
                $scope.getGaugeValueList();
            }
        }
        return true;
    };

    $scope.getGaugeValueList = function () {
        if (!$scope.tmp.gaugeName) {
            $scope.tmp.containerGaugeChartData = { data: [], xData: [] };
            return;
        }

        $http.get('/dms/node/metric/queue', {
            params: {
                nodeIp: $scope.tmp.targetContainer.Node_Ip,
                containerId: $scope.tmp.targetContainer.Id,
                queueType: 'app',
                type: 'guage',
                gaugeName: $scope.tmp.gaugeName
            }
        }).success(function (data) {
            $scope.tmp.containerGaugeChartData = { data: data.list, xData: data.timelineList };
            Page.fixCenter(lhgdialog.list['dialogContainerStats_1']);
        });
    };
});