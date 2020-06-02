var md = angular.module('module_cluster/overview', ['base']);
md.controller('MainCtrl', function ($scope, $http, uiTips, uiValid) {
    $scope.tmp = {};
    $scope.ctrl = {};

    $http.get('/dms/cluster/list/simple', { params: {} }).success(function (data) {
        $scope.clusterList = data;
        if (data.length) {
            $scope.tmp.clusterId = data[0].id;
            $scope.onClusterChoose();
        }
    });

    var groupByToList = function (groupByObj) {
        var r = [];
        for (k in groupByObj) {
            var list = groupByObj[k];
            var first = list[0];
            r.push({ key: k, appName: first.appName, appDes: first.appDes, 
                clusterId: first.Cluster_Id, namespaceId: first.namespaceId, list: list });
        }
        return r;
    };

    $scope.changeNodeListInPage = function (pageNum) {
        if (!$scope.tmp.nodeAllList) {
            return;
        }

        var nodeList = $scope.tmp.nodeAllList;
        var pageSize = 5;
        var cc = nodeList.length;
        var currentPage = pageNum || 1;

        var beginIndex = (currentPage - 1) * pageSize;
        var endIndex = currentPage * pageSize;

        if (beginIndex > cc - 1)
            beginIndex = 0;
        if (endIndex > cc)
            endIndex = cc;

        $scope.nodeList = nodeList.slice(beginIndex, endIndex);
        $scope.nodePager = { pageNum: currentPage, pageSize: pageSize, totalCount: cc };
    };

    $scope.onClusterChoose = function () {
        $http.get('/dms/container/manage/list', { params: { clusterId: $scope.tmp.clusterId } }).success(function (data) {
            $scope.groupByApp = groupByToList(data.groupByApp);
            $scope.groupByNodeIp = groupByToList(data.groupByNodeIp);
            var appCheckOkList = data.appCheckOkList;

            $scope.appChartData = [{
                name: 'Check OK', value: _.filter(appCheckOkList, function (it) {
                    return it.isOk;
                }).length
            }, {
                name: 'Check Fail', value: _.filter(appCheckOkList, function (it) {
                    return !it.isOk;
                }).length
            }];
        });

        $http.get('/dms/node/list', { params: { clusterId: $scope.tmp.clusterId } }).success(function (data) {
            // for test sort and node pagination
            // if (data.length == 1) {
            //     var first = data[0];
            //     var second = _.clone(first);
            //     second.cpuUsedPercent = second.cpuUsedPercent - 1;
            //     second.memoryUsedPercent = second.memoryUsedPercent - 1;
            //     second.nodeIp = '0.0.0.0';

            //     var third = _.clone(first);
            //     third.cpuUsedPercent = second.cpuUsedPercent - 2;
            //     third.memoryUsedPercent = second.memoryUsedPercent - 2;
            //     third.nodeIp = '0.0.0.1';

            //     $scope.tmp.nodeAllList = [first, second, third];
            // } else {
            $scope.tmp.nodeAllList = data;
            // }
            $scope.changeNodeListInPage();

            $scope.nodeChartData = [{
                name: 'Agent OK', value: _.filter($scope.nodeList, function (it) {
                    return it.isOk;
                }).length
            }, {
                name: 'Agent Fail', value: _.filter($scope.nodeList, function (it) {
                    return !it.isOk;
                }).length
            }];

            $scope.nodeMemChartData = [{
                name: 'Free', value: Math.round(_.reduce($scope.nodeList, function (num, it) {
                    return it.memoryFreeMB / 1024 + num;
                }, 0), 2)
            }, {
                name: 'Used', value: Math.round(_.reduce($scope.nodeList, function (num, it) {
                    return it.memoryTotalMB / 1024 - it.memoryFreeMB / 1024 + num;
                }, 0), 2)
            }];

            $scope.nodeCpuChartData = [{
                name: 'Idle', value: Math.round(_.reduce($scope.nodeList, function (num, it) {
                    return it.cpuIdle * 100 + num;
                }, 0), 2)
            }, {
                name: 'Sys', value: Math.round(_.reduce($scope.nodeList, function (num, it) {
                    return it.cpuSys * 100 + num;
                }, 0), 2)
            }, {
                name: 'User', value: Math.round(_.reduce($scope.nodeList, function (num, it) {
                    return it.cpuUser * 100 + num;
                }, 0), 2)
            }];
        });
    };

    $scope.goAppOne = function (one) {
        Page.go('/page/cluster_container', {
            appId: one.key, appName: one.appName, appDes: one.appDes,
            clusterId: one.clusterId,
            namespaceId: one.namespaceId
        });
    };

    $scope.updateTags = function (id, ip, tags) {
        uiTips.prompt('Update tags for - ' + ip, function (val) {
            $http.get('/dms/node/tag/update', { params: { id: id, tags: val || '' } }).success(function (data) {
                $scope.onClusterChoose();
            });
        }, tags || '');
    };

    // chart test
    // $scope.appChartData = [{ name: 'app1', value: 40 }, { name: 'app2', value: 80 }];

    var isCpuUp = false;
    $scope.sortCpu = function () {
        $scope.nodeList = _.sortBy($scope.nodeList, function (it) {
            return isCpuUp ? -it.cpuUsedPercent : it.cpuUsedPercent;
        });
        if (!isCpuUp) {
            $scope.tmp.isSortUpCpu = true;
            $scope.tmp.isSortDownCpu = false;
            $scope.tmp.isSortUpMem = false;
            $scope.tmp.isSortDownMem = false;
            isCpuUp = true;
        } else {
            $scope.tmp.isSortUpCpu = false;
            $scope.tmp.isSortDownCpu = true;
            $scope.tmp.isSortUpMem = false;
            $scope.tmp.isSortDownMem = false;
            isCpuUp = false;
        }
    };

    var isMemUp = false;
    $scope.sortMem = function () {
        $scope.nodeList = _.sortBy($scope.nodeList, function (it) {
            return isMemUp ? -it.memoryUsedPercent : it.memoryUsedPercent;
        });
        if (!isMemUp) {
            $scope.tmp.isSortUpCpu = false;
            $scope.tmp.isSortDownCpu = false;
            $scope.tmp.isSortUpMem = true;
            $scope.tmp.isSortDownMem = false;
            isMemUp = true;
        } else {
            $scope.tmp.isSortUpCpu = false;
            $scope.tmp.isSortDownCpu = false;
            $scope.tmp.isSortUpMem = false;
            $scope.tmp.isSortDownMem = true;
            isMemUp = false;
        }
    };

    var currentNodeIp;
    $scope.queryEventLl = function (pageNum) {
        var p = {
            pageNum: pageNum || 1,
            reason: $scope.tmp.reason
        };
        if (isQueryEventForCluster) {
            p.type = 'cluster';
        } else {
            p.type = 'node';
            p.nodeIp = currentNodeIp;
        }
        $http.get('/dms/event/list', { params: p }).success(function (data) {
            $scope.eventLl = data.list;
            $scope.eventPager = { pageNum: data.pageNum, pageSize: data.pageSize, totalCount: data.totalCount };
        });
    };

    $scope.queryEventReasonLl = function (one) {
        currentNodeIp = one.nodeIp;
        $scope.tmp.eventTarget = 'Node Ip - ' + currentNodeIp;
        isQueryEventForCluster = false;
        $http.get('/dms/event/reason/list', { params: { type: 'node', nodeIp: currentNodeIp } }).success(function (data) {
            $scope.tmp.reasonList = data;
            $scope.queryEventLl();
        });
    };

    var isQueryEventForCluster = false;
    $scope.queryEventReasonLlForCluster = function () {
        isQueryEventForCluster = true;
        $scope.tmp.eventTarget = 'Cluster Id - ' + $scope.tmp.clusterId;
        $http.get('/dms/event/reason/list', { params: { type: 'cluster' } }).success(function (data) {
            $scope.tmp.reasonList = data;
            $scope.queryEventLl();
        });
    };

    $scope.showStats = function (one) {
        $scope.tmp.targetNode = one;
        $scope.ctrl.isShowNodeStats = true;
    };

    var Page = window.Page;
    $scope.changeNodeStatsTab = function (index, triggerIndex) {
        if (index == 1) {
            $http.get('/dms/node/metric/queue', { params: { nodeIp: $scope.tmp.targetNode.nodeIp, type: 'cpu' } }).
                success(function (data) {
                    $scope.tmp.nodeCpuChartData = { data: data.list, xData: data.timelineList };
                    Page.fixCenter(lhgdialog.list['dialogNodeStats_1']);
                });
        } else if (index == 2) {
            $http.get('/dms/node/metric/queue', { params: { nodeIp: $scope.tmp.targetNode.nodeIp, type: 'mem' } }).
                success(function (data) {
                    $scope.tmp.nodeMemChartData = { data: data.list, xData: data.timelineList };
                    Page.fixCenter(lhgdialog.list['dialogNodeStats_1']);
                });

        }
        return true;
    };
});