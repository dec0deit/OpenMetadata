import { AxiosPromise, AxiosResponse } from 'axios';
import classNames from 'classnames';
import { compare } from 'fast-json-patch';
import { isNil } from 'lodash';
import { ColumnTags, TableDetail } from 'Models';
import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getChartById, updateChart } from '../../axiosAPIs/chartAPI';
import {
  addFollower,
  getDashboardByFqn,
  patchDashboardDetails,
  removeFollower,
} from '../../axiosAPIs/dashboardAPI';
import { getServiceById } from '../../axiosAPIs/serviceAPI';
import Description from '../../components/common/description/Description';
import EntityPageInfo from '../../components/common/entityPageInfo/EntityPageInfo';
import RichTextEditorPreviewer from '../../components/common/rich-text-editor/RichTextEditorPreviewer';
import TabsPane from '../../components/common/TabsPane/TabsPane';
import { TitleBreadcrumbProps } from '../../components/common/title-breadcrumb/title-breadcrumb.interface';
import PageContainer from '../../components/containers/PageContainer';
import Loader from '../../components/Loader/Loader';
import { ModalWithMarkdownEditor } from '../../components/Modals/ModalWithMarkdownEditor/ModalWithMarkdownEditor';
import ManageTab from '../../components/my-data-details/ManageTab';
import { getServiceDetailsPath } from '../../constants/constants';
import { Chart } from '../../generated/entity/data/chart';
import { Dashboard, TagLabel } from '../../generated/entity/data/dashboard';
import {
  getCurrentUserId,
  getUserTeams,
  isEven,
} from '../../utils/CommonUtils';
import { serviceTypeLogo } from '../../utils/ServiceUtils';
import SVGIcons from '../../utils/SvgUtils';
import {
  getOwnerFromId,
  getTagsWithoutTier,
  getTierFromTableTags,
  getUsagePercentile,
} from '../../utils/TableUtils';
import { getTagCategories, getTaglist } from '../../utils/TagsUtils';
type ChartType = {
  displayName: string;
} & Chart;
const MyDashBoardPage = () => {
  const USERId = getCurrentUserId();
  const [tagList, setTagList] = useState<Array<string>>([]);
  const { dashboardFQN } = useParams() as Record<string, string>;
  const [dashboardDetails, setDashboardDetails] = useState<Dashboard>(
    {} as Dashboard
  );
  const [dashboardId, setDashboardId] = useState<string>('');
  const [isLoading, setLoading] = useState<boolean>(false);
  const [description, setDescription] = useState<string>('');
  const [followers, setFollowers] = useState<number>(0);
  const [isFollowing, setIsFollowing] = useState(false);
  const [owner, setOwner] = useState<TableDetail['owner']>();
  const [tier, setTier] = useState<string>();
  const [tags, setTags] = useState<Array<ColumnTags>>([]);
  const [activeTab, setActiveTab] = useState<number>(1);
  const [isEdit, setIsEdit] = useState<boolean>(false);
  const [usage, setUsage] = useState('');
  const [charts, setCharts] = useState<ChartType[]>([]);
  const [weeklyUsageCount, setWeeklyUsageCount] = useState('');
  const [slashedDashboardName, setSlashedDashboardName] = useState<
    TitleBreadcrumbProps['titleLinks']
  >([]);

  const [editChart, setEditChart] = useState<{
    chart: ChartType;
    index: number;
  }>();

  const hasEditAccess = () => {
    if (owner?.type === 'user') {
      return owner.id === getCurrentUserId();
    } else {
      return getUserTeams().some((team) => team.id === owner?.id);
    }
  };
  const tabs = [
    {
      name: 'Details',
      icon: {
        alt: 'schema',
        name: 'icon-schema',
        title: 'Details',
      },
      isProtected: false,
      position: 1,
    },
    {
      name: 'Manage',
      icon: {
        alt: 'manage',
        name: 'icon-manage',
        title: 'Manage',
      },
      isProtected: true,
      protectedState: !owner || hasEditAccess(),
      position: 2,
    },
  ];

  const extraInfo = [
    { key: 'Owner', value: owner?.name || '' },
    { key: 'Tier', value: tier ? tier.split('.')[1] : '' },
    { key: 'Usage', value: usage },
    { key: 'Queries', value: `${weeklyUsageCount} past week` },
  ];
  const fetchTags = () => {
    getTagCategories().then((res) => {
      if (res.data) {
        setTagList(getTaglist(res.data));
      }
    });
  };

  const fetchCharts = async (charts: Dashboard['charts']) => {
    let chartsData: ChartType[] = [];
    let promiseArr: Array<AxiosPromise> = [];
    if (charts?.length) {
      promiseArr = charts.map((chart) => getChartById(chart.id, 'service'));
      await Promise.all(promiseArr).then((res: Array<AxiosResponse>) => {
        if (res.length) {
          chartsData = res.map((chart) => chart.data);
        }
      });
    }

    return chartsData;
  };

  const fetchDashboardDetail = (dashboardFQN: string) => {
    setLoading(true);
    getDashboardByFqn(dashboardFQN, [
      'owner',
      'service',
      'followers',
      'tags',
      'usageSummary',
      'charts',
    ]).then((res: AxiosResponse) => {
      const {
        id,
        description,
        followers,
        service,
        tags,
        owner,
        usageSummary,
        displayName,
        charts,
      } = res.data;
      setDashboardDetails(res.data);
      setDashboardId(id);
      setDescription(description ?? '');
      setFollowers(followers?.length);
      setOwner(getOwnerFromId(owner?.id));
      setTier(getTierFromTableTags(tags));
      setTags(getTagsWithoutTier(tags));
      setIsFollowing(followers.some(({ id }: { id: string }) => id === USERId));
      if (!isNil(usageSummary?.weeklyStats.percentileRank)) {
        const percentile = getUsagePercentile(
          usageSummary.weeklyStats.percentileRank
        );
        setUsage(percentile);
      } else {
        setUsage('--');
      }
      setWeeklyUsageCount(
        usageSummary?.weeklyStats.count.toLocaleString() || '--'
      );
      getServiceById('dashboardServices', service?.id).then(
        (serviceRes: AxiosResponse) => {
          setSlashedDashboardName([
            {
              name: serviceRes.data.name,
              url: serviceRes.data.name
                ? getServiceDetailsPath(
                    serviceRes.data.name,
                    serviceRes.data.serviceType
                  )
                : '',
              imgSrc: serviceRes.data.serviceType
                ? serviceTypeLogo(serviceRes.data.serviceType)
                : undefined,
            },
            {
              name: displayName,
              url: '',
              activeTitle: true,
            },
          ]);
        }
      );
      fetchCharts(charts).then((charts) => setCharts(charts));

      setLoading(false);
    });
  };

  const followDashboard = (): void => {
    if (isFollowing) {
      removeFollower(dashboardId, USERId).then(() => {
        setFollowers((preValu) => preValu - 1);
        setIsFollowing(false);
      });
    } else {
      addFollower(dashboardId, USERId).then(() => {
        setFollowers((preValu) => preValu + 1);
        setIsFollowing(true);
      });
    }
  };

  const onDescriptionUpdate = (updatedHTML: string) => {
    const updatedDashboard = { ...dashboardDetails, description: updatedHTML };

    const jsonPatch = compare(dashboardDetails, updatedDashboard);
    patchDashboardDetails(dashboardId, jsonPatch).then((res: AxiosResponse) => {
      setDescription(res.data.description);
    });
    setIsEdit(false);
  };
  const onDescriptionEdit = (): void => {
    setIsEdit(true);
  };
  const onCancel = () => {
    setIsEdit(false);
  };

  const onSettingsUpdate = (
    newOwner?: TableDetail['owner'],
    newTier?: TableDetail['tier']
  ): Promise<void> => {
    return new Promise<void>((resolve, reject) => {
      if (newOwner || newTier) {
        const tierTag: TableDetail['tags'] = newTier
          ? [
              ...getTagsWithoutTier(dashboardDetails.tags as ColumnTags[]),
              { tagFQN: newTier, labelType: 'Manual', state: 'Confirmed' },
            ]
          : (dashboardDetails.tags as ColumnTags[]);
        const updatedDashboard = {
          ...dashboardDetails,
          owner: newOwner
            ? { ...dashboardDetails.owner, ...newOwner }
            : dashboardDetails.owner,
          tags: tierTag,
        };
        const jsonPatch = compare(dashboardDetails, updatedDashboard);
        patchDashboardDetails(dashboardId, jsonPatch)
          .then((res: AxiosResponse) => {
            setDashboardDetails(res.data);
            setOwner(getOwnerFromId(res.data.owner?.id));
            setTier(getTierFromTableTags(res.data.tags));
            resolve();
          })
          .catch(() => reject());
      } else {
        reject();
      }
    });
  };

  const onTagUpdate = (selectedTags?: Array<string>) => {
    if (selectedTags) {
      const prevTags = dashboardDetails?.tags?.filter((tag) =>
        selectedTags.includes(tag?.tagFQN as string)
      );
      const newTags: Array<ColumnTags> = selectedTags
        .filter((tag) => {
          return !prevTags?.map((prevTag) => prevTag.tagFQN).includes(tag);
        })
        .map((tag) => ({
          labelType: 'Manual',
          state: 'Confirmed',
          tagFQN: tag,
        }));
      const updatedTags = [...(prevTags as TagLabel[]), ...newTags];
      const updatedDashboard = { ...dashboardDetails, tags: updatedTags };
      const jsonPatch = compare(dashboardDetails, updatedDashboard);
      patchDashboardDetails(dashboardId, jsonPatch).then(
        (res: AxiosResponse) => {
          setTier(getTierFromTableTags(res.data.tags));
          setTags(getTagsWithoutTier(res.data.tags));
        }
      );
    }
  };

  const handleUpdateChart = (chart: ChartType, index: number) => {
    setEditChart({ chart, index });
  };

  const closeEditChartModal = (): void => {
    setEditChart(undefined);
  };

  const onChartUpdate = (chartDescription: string) => {
    if (editChart) {
      const updatedChart = {
        ...editChart.chart,
        description: chartDescription,
      };
      const jsonPatch = compare(charts[editChart.index], updatedChart);
      updateChart(editChart.chart.id, jsonPatch).then((res: AxiosResponse) => {
        if (res.data) {
          setCharts((prevCharts) => {
            const charts = [...prevCharts];
            charts[editChart.index] = res.data;

            return charts;
          });
        }
      });
      setEditChart(undefined);
    } else {
      setEditChart(undefined);
    }
  };

  useEffect(() => {
    fetchDashboardDetail(dashboardFQN);
  }, [dashboardFQN]);

  useEffect(() => {
    fetchTags();
  }, []);

  return (
    <PageContainer>
      {isLoading ? (
        <Loader />
      ) : (
        <div className="tw-px-4 w-full">
          <EntityPageInfo
            isTagEditable
            extraInfo={extraInfo}
            followers={followers}
            followHandler={followDashboard}
            isFollowing={isFollowing}
            tagList={tagList}
            tags={tags}
            tagsHandler={onTagUpdate}
            tier={tier || ''}
            titleLinks={slashedDashboardName}
          />
          <div className="tw-block tw-mt-1">
            <TabsPane
              activeTab={activeTab}
              setActiveTab={setActiveTab}
              tabs={tabs}
            />

            <div className="tw-bg-white tw--mx-4 tw-p-4">
              {activeTab === 1 && (
                <>
                  <div className="tw-grid tw-grid-cols-4 tw-gap-4 w-full">
                    <div className="tw-col-span-full">
                      <Description
                        description={description}
                        hasEditAccess={hasEditAccess()}
                        isEdit={isEdit}
                        owner={owner}
                        onCancel={onCancel}
                        onDescriptionEdit={onDescriptionEdit}
                        onDescriptionUpdate={onDescriptionUpdate}
                      />
                    </div>
                  </div>
                  <div className="tw-table-responsive tw-my-6">
                    <table className="tw-w-full" data-testid="schema-table">
                      <thead>
                        <tr className="tableHead-row">
                          <th className="tableHead-cell">Name</th>
                          <th className="tableHead-cell">Description</th>
                          <th className="tableHead-cell">Type</th>
                        </tr>
                      </thead>
                      <tbody className="tableBody">
                        {charts.map((chart, index) => (
                          <tr
                            className={classNames(
                              'tableBody-row',
                              !isEven(index + 1) ? 'odd-row' : null
                            )}
                            key={index}>
                            <td className="tableBody-cell">
                              <Link
                                target="_blank"
                                to={{ pathname: chart.chartUrl }}>
                                {chart.displayName}
                              </Link>
                            </td>
                            <td className="tw-group tableBody-cell tw-relative">
                              <div
                                className="tw-cursor-pointer hover:tw-underline tw-flex"
                                data-testid="description"
                                onClick={() => handleUpdateChart(chart, index)}>
                                <div>
                                  {chart.description ? (
                                    <RichTextEditorPreviewer
                                      markdown={chart.description}
                                    />
                                  ) : (
                                    <span className="tw-no-description">
                                      No description added
                                    </span>
                                  )}
                                </div>
                                <button className="tw-self-start tw-w-8 tw-h-auto tw-opacity-0 tw-ml-1 group-hover:tw-opacity-100 focus:tw-outline-none">
                                  <SVGIcons
                                    alt="edit"
                                    icon="icon-edit"
                                    title="Edit"
                                    width="10px"
                                  />
                                </button>
                              </div>
                            </td>
                            <td className="tableBody-cell">
                              {chart.chartType}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
              {activeTab === 2 && (
                <ManageTab
                  currentTier={tier}
                  currentUser={owner?.id}
                  hasEditAccess={hasEditAccess()}
                  onSave={onSettingsUpdate}
                />
              )}
            </div>
          </div>
        </div>
      )}
      {editChart && (
        <ModalWithMarkdownEditor
          header={`Edit Chart: "${editChart.chart.displayName}"`}
          placeholder="Enter Chart Description"
          value={editChart.chart.description || ''}
          onCancel={closeEditChartModal}
          onSave={onChartUpdate}
        />
      )}
    </PageContainer>
  );
};

export default MyDashBoardPage;